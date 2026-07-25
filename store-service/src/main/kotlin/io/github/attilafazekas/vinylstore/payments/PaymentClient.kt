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

package io.github.attilafazekas.vinylstore.payments

import io.github.attilafazekas.vinylstore.enums.PaymentChargeStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private const val PAYMENT_CLIENT_TIMEOUT_MILLIS = 5_000L

class PaymentClient(
    private val baseUrl: String,
    private val client: HttpClient = createHttpClient(),
) {
    suspend fun charge(request: PaymentChargeRequest): PaymentChargeOutcome =
        try {
            val response =
                client.post("$baseUrl/payments") {
                    setBody(request)
                }
            if (response.status.isSuccess()) {
                val chargeResponse = response.body<PaymentChargeResponse>()
                when (chargeResponse.status) {
                    PaymentChargeStatus.Succeeded -> PaymentChargeOutcome.Approved(chargeResponse)
                    PaymentChargeStatus.Failed -> PaymentChargeOutcome.Declined(chargeResponse)
                }
            } else {
                PaymentChargeOutcome.Unavailable(IllegalStateException("Payment service responded with ${response.status}"))
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            PaymentChargeOutcome.Unavailable(cause)
        }
}

sealed interface PaymentChargeOutcome {
    data class Approved(
        val response: PaymentChargeResponse,
    ) : PaymentChargeOutcome

    data class Declined(
        val response: PaymentChargeResponse,
    ) : PaymentChargeOutcome

    data class Unavailable(
        val cause: Throwable,
    ) : PaymentChargeOutcome
}

private fun createHttpClient(): HttpClient =
    HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = PAYMENT_CLIENT_TIMEOUT_MILLIS
            connectTimeoutMillis = PAYMENT_CLIENT_TIMEOUT_MILLIS
        }
    }
