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

package io.github.attilafazekas.paymentservice

import io.github.attilafazekas.paymentservice.enums.PaymentStatus
import io.github.attilafazekas.paymentservice.models.ErrorResponse
import io.github.attilafazekas.paymentservice.models.PaymentRequest
import io.github.attilafazekas.paymentservice.models.PaymentResponse
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

private fun testChargeRequest(
    paymentMethod: String = "tok_visa",
    idempotencyKey: String = Uuid.random().toString(),
) = PaymentRequest(
    orderReference = Uuid.random().toString(),
    amountCents = 3499,
    currency = "EUR",
    paymentMethod = paymentMethod,
    idempotencyKey = idempotencyKey,
)

class PaymentRoutesTest {
    @Test
    fun `charge without API key returns 401`() =
        testApplication {
            application { paymentServiceApplication() }

            val response = unauthenticatedClient().post("/payments") { setBody(testChargeRequest()) }

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `charge with wrong API key returns 401`() =
        testApplication {
            application { paymentServiceApplication() }

            val response =
                unauthenticatedClient().post("/payments") {
                    header(API_KEY_HEADER, "wrong-key")
                    setBody(testChargeRequest())
                }

            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ErrorResponse>().error shouldBe UNAUTHORIZED
        }

    @Test
    fun `charge with a non-declined payment method succeeds`() =
        testApplication {
            application { paymentServiceApplication() }

            val response = authenticatedClient().post("/payments") { setBody(testChargeRequest()) }

            response.status shouldBe HttpStatusCode.OK
            val payment = response.body<PaymentResponse>()
            payment.status shouldBe PaymentStatus.Succeeded
            payment.failureReason shouldBe null
        }

    @Test
    fun `charge with tok_chargeDeclined is declined`() =
        testApplication {
            application { paymentServiceApplication() }

            val response =
                authenticatedClient().post("/payments") {
                    setBody(testChargeRequest(paymentMethod = "tok_chargeDeclined"))
                }

            response.status shouldBe HttpStatusCode.OK
            val payment = response.body<PaymentResponse>()
            payment.status shouldBe PaymentStatus.Failed
            payment.failureReason shouldBe "Card declined"
        }

    @Test
    fun `retrying with the same idempotencyKey replays the original response`() =
        testApplication {
            application { paymentServiceApplication() }
            val client = authenticatedClient()
            val idempotencyKey = Uuid.random().toString()

            val first = client.post("/payments") { setBody(testChargeRequest(idempotencyKey = idempotencyKey)) }
            val second =
                client.post("/payments") {
                    setBody(testChargeRequest(paymentMethod = "tok_chargeDeclined", idempotencyKey = idempotencyKey))
                }

            first.body<PaymentResponse>() shouldBe second.body<PaymentResponse>()
        }

    @Test
    fun `get payment returns a previously created charge`() =
        testApplication {
            application { paymentServiceApplication() }
            val client = authenticatedClient()
            val charged = client.post("/payments") { setBody(testChargeRequest()) }.body<PaymentResponse>()

            val response = client.get("/payments/${charged.paymentId}")

            response.status shouldBe HttpStatusCode.OK
            response.body<PaymentResponse>() shouldBe charged
        }

    @Test
    fun `get payment for an unknown ID returns 404`() =
        testApplication {
            application { paymentServiceApplication() }

            val response = authenticatedClient().get("/payments/${Uuid.random()}")

            response.status shouldBe HttpStatusCode.NotFound
            response.body<ErrorResponse>().error shouldBe NOT_FOUND
        }

    @Test
    fun `get payment with a malformed ID returns 400`() =
        testApplication {
            application { paymentServiceApplication() }

            val response = authenticatedClient().get("/payments/not-a-uuid")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorResponse>().error shouldBe BAD_REQUEST
        }

    @Test
    fun `get payment without API key returns 401`() =
        testApplication {
            application { paymentServiceApplication() }

            val response = unauthenticatedClient().get("/payments/${Uuid.random()}")

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    private fun ApplicationTestBuilder.unauthenticatedClient() =
        createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(DefaultRequest) {
                contentType(ContentType.Application.Json)
            }
        }

    private fun ApplicationTestBuilder.authenticatedClient() =
        createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(DefaultRequest) {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, DEFAULT_PAYMENT_SERVICE_API_KEY)
            }
        }
}
