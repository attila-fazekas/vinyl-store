/*
 * Copyright 2026 Attila Fazekas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.attilafazekas.vinylstore

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.enums.OrderStatus
import io.github.attilafazekas.vinylstore.enums.PaymentChargeStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Address
import io.github.attilafazekas.vinylstore.models.CreateOrderRequest
import io.github.attilafazekas.vinylstore.models.Listing
import io.github.attilafazekas.vinylstore.models.OrderResponse
import io.github.attilafazekas.vinylstore.models.PayOrderRequest
import io.github.attilafazekas.vinylstore.payments.PaymentClient
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.komapper.r2dbc.R2dbcDatabase
import kotlin.uuid.Uuid

class OrderPaymentFlowTest {
    @Test
    fun `pay succeeds - order becomes Paid and inventory converts reserved to sold`() {
        withCheckoutFixture { store, wireMock, client, fixture, order ->
            wireMock.stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/payments"))
                    .willReturn(WireMock.okJson(paymentResponseJson(PaymentChargeStatus.Succeeded))),
            )

            val response = client.payOrder(fixture.token, order.order.id)
            response.status shouldBe HttpStatusCode.OK
            response.body<OrderResponse>().order.status shouldBe OrderStatus.Paid

            val inventory = store.getInventoryByListingId(fixture.listing.id)!!
            inventory.totalQuantity shouldBe fixture.initialStock - fixture.quantity
            inventory.reservedQuantity shouldBe 0
        }
    }

    @Test
    fun `pay declined - order becomes Failed and reservation is released`() {
        withCheckoutFixture { store, wireMock, client, fixture, order ->
            wireMock.stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/payments"))
                    .willReturn(WireMock.okJson(paymentResponseJson(PaymentChargeStatus.Failed))),
            )

            val response = client.payOrder(fixture.token, order.order.id)
            response.status shouldBe HttpStatusCode.OK
            response.body<OrderResponse>().order.status shouldBe OrderStatus.Failed

            val inventory = store.getInventoryByListingId(fixture.listing.id)!!
            inventory.totalQuantity shouldBe fixture.initialStock
            inventory.reservedQuantity shouldBe 0
        }
    }

    @Test
    fun `payment service 500 - order stays Pending and endpoint reports unavailable`() {
        withCheckoutFixture { store, wireMock, client, fixture, order ->
            wireMock.stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/payments"))
                    .willReturn(WireMock.serverError()),
            )

            val response = client.payOrder(fixture.token, order.order.id)
            response.status shouldBe HttpStatusCode.ServiceUnavailable

            store.getOrderById(order.order.id)!!.status shouldBe OrderStatus.Pending
            val inventory = store.getInventoryByListingId(fixture.listing.id)!!
            inventory.totalQuantity shouldBe fixture.initialStock
            inventory.reservedQuantity shouldBe fixture.quantity
        }
    }

    @Test
    fun `payment service timeout - order stays Pending and endpoint reports unavailable`() {
        withCheckoutFixture(paymentClientFactory = { baseUrl ->
            PaymentClient(
                baseUrl,
                shortTimeoutHttpClient(),
            )
        }) { store, wireMock, client, fixture, order ->
            wireMock.stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/payments"))
                    .willReturn(
                        WireMock
                            .okJson(paymentResponseJson(PaymentChargeStatus.Succeeded))
                            .withFixedDelay(TIMEOUT_TEST_DELAY_MILLIS),
                    ),
            )

            val response = client.payOrder(fixture.token, order.order.id)
            response.status shouldBe HttpStatusCode.ServiceUnavailable

            store.getOrderById(order.order.id)!!.status shouldBe OrderStatus.Pending
            val inventory = store.getInventoryByListingId(fixture.listing.id)!!
            inventory.totalQuantity shouldBe fixture.initialStock
            inventory.reservedQuantity shouldBe fixture.quantity
        }
    }

    @Test
    fun `double-charge guard - paying an already Paid order returns 409 without a second charge call`() {
        withCheckoutFixture { store, wireMock, client, fixture, order ->
            wireMock.stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/payments"))
                    .willReturn(WireMock.okJson(paymentResponseJson(PaymentChargeStatus.Succeeded))),
            )

            client.payOrder(fixture.token, order.order.id).status shouldBe HttpStatusCode.OK
            store.getOrderById(order.order.id)!!.status shouldBe OrderStatus.Paid

            val secondAttempt = client.payOrder(fixture.token, order.order.id)
            secondAttempt.status shouldBe HttpStatusCode.Conflict

            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/payments")))
        }
    }

    private fun withCheckoutFixture(
        paymentClientFactory: (String) -> PaymentClient = { baseUrl -> PaymentClient(baseUrl) },
        block: suspend (
            store: VinylStoreRepository,
            wireMock: WireMockServer,
            client: HttpClient,
            fixture: CheckoutFixture,
            order: OrderResponse,
        ) -> Unit,
    ) {
        val wireMock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()
        try {
            testApplication {
                val store =
                    VinylStoreRepository(R2dbcDatabase("r2dbc:postgresql://vinylstore:vinylstore@localhost/vinylstore"))
                val paymentClient = paymentClientFactory(wireMock.baseUrl())
                application { vinylStoreApplication(store = store, paymentClient = paymentClient) }

                val client =
                    createClient {
                        install(ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                        install(DefaultRequest) {
                            contentType(ContentType.Application.Json)
                        }
                    }

                val fixture = seedCheckoutFixture(store)
                val order = client.createPendingOrder(fixture.token, fixture.address.id)
                block(store, wireMock, client, fixture, order)
            }
        } finally {
            wireMock.stop()
        }
    }

    private suspend fun seedCheckoutFixture(
        store: VinylStoreRepository,
        initialStock: Int = 10,
        quantity: Int = 2,
    ): CheckoutFixture {
        val suffix = Uuid.random()
        val user = store.createUser(Email("customer-$suffix@example.com"), Password("password123"), Role.Customer)
        val address =
            store.createAddress(
                userId = user.id,
                type = AddressType.Shipping,
                fullName = "Test Customer",
                street = "1 Test Street",
                city = "Testville",
                postalCode = "00000",
                country = "Testland",
                isDefault = true,
            )
        val artist = store.createArtist("Artist $suffix")
        val label = store.createLabel("Label $suffix")
        val genre = store.createGenre("Genre $suffix")
        val vinyl = store.createVinyl("Vinyl $suffix", artist.id, label.id, genre.id, 2020, "M", "M")
        val listing = store.createListing(vinyl.id, PAYMENT_TEST_UNIT_PRICE, "EUR", initialStock)
        store.upsertCartItem(user.id, listing.id, quantity)
        val token = JwtConfig.generateToken(user.id, user.email, user.role)
        return CheckoutFixture(address, listing, token, initialStock, quantity)
    }

    private suspend fun HttpClient.createPendingOrder(
        token: String,
        addressId: Uuid,
    ): OrderResponse {
        val response =
            post("/v1/orders") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(CreateOrderRequest(addressId))
            }
        response.status shouldBe HttpStatusCode.Created
        return response.body()
    }

    private suspend fun HttpClient.payOrder(
        token: String,
        orderId: Uuid,
    ): HttpResponse =
        post("/v1/orders/$orderId/pay") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(PayOrderRequest("tok_visa"))
        }

    private fun paymentResponseJson(status: PaymentChargeStatus): String =
        """
        {
          "paymentId": "${Uuid.random()}",
          "status": "$status",
          "orderReference": "${Uuid.random()}",
          "amountCents": 6998,
          "currency": "EUR",
          "createdAt": "${TimestampUtil.now()}"
        }
        """.trimIndent()

    private fun shortTimeoutHttpClient(): HttpClient =
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = SHORT_CLIENT_TIMEOUT_MILLIS
                connectTimeoutMillis = SHORT_CLIENT_TIMEOUT_MILLIS
            }
        }

    private data class CheckoutFixture(
        val address: Address,
        val listing: Listing,
        val token: String,
        val initialStock: Int,
        val quantity: Int,
    )

    private companion object {
        const val PAYMENT_TEST_UNIT_PRICE = 34.99
        const val SHORT_CLIENT_TIMEOUT_MILLIS = 500L
        const val TIMEOUT_TEST_DELAY_MILLIS = 1_500
    }
}
