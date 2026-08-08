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

package io.github.attilafazekas.paymentservice.routes

import io.github.attilafazekas.paymentservice.enums.PaymentStatus
import io.github.attilafazekas.paymentservice.models.HealthResponse
import io.github.attilafazekas.paymentservice.models.PaymentRequest
import io.github.attilafazekas.paymentservice.models.PaymentResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.uuid.Uuid

fun Route.paymentRoutes() {
    post("/payments", chargePaymentDocumentation()) {
        call.respond(HttpStatusCode.NotImplemented)
    }

    get("/payments/{paymentId}", getPaymentDocumentation()) {
        call.respond(HttpStatusCode.NotImplemented)
    }

    get("/health", healthCheckDocumentation()) {
        call.respond(HealthResponse(status = "OK"))
    }
}

private fun chargePaymentDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "chargePayment"
        summary = "Charge Payment"
        description =
            """
            Charge a payment method for a given order.

            **This endpoint is not implemented yet.** It documents the intended request and response
            contract so that calling services (such as vinylstore) can be built and tested against a
            stable schema, for example by stubbing this endpoint with WireMock.

            **Intended Behavior:**
            - Charges are synchronous: the response reflects the final outcome (Succeeded or Failed)
            - Retrying a charge with the same idempotencyKey must not result in a double charge
            """.trimIndent()
        tags = listOf("payments")
        request {
            body<PaymentRequest> {
                description = "Details of the charge to attempt."
                example("Charge request") {
                    value =
                        PaymentRequest(
                            orderReference = "550e8400-e29b-41d4-a716-446655440000",
                            amountCents = 3499,
                            currency = "EUR",
                            paymentMethod = "tok_visa",
                            idempotencyKey = "550e8400-e29b-41d4-a716-446655440000",
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The intended response shape once this endpoint is implemented."
                body<PaymentResponse> {
                    example("Payment succeeded") {
                        value =
                            PaymentResponse(
                                paymentId = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                                status = PaymentStatus.Succeeded,
                                orderReference = "550e8400-e29b-41d4-a716-446655440000",
                                amountCents = 3499,
                                currency = "EUR",
                                failureReason = null,
                                createdAt = "2025-01-10T14:30:45.123Z",
                            )
                    }
                    example("Payment failed") {
                        value =
                            PaymentResponse(
                                paymentId = Uuid.parse("660e8400-e29b-41d4-a716-446655440001"),
                                status = PaymentStatus.Failed,
                                orderReference = "550e8400-e29b-41d4-a716-446655440000",
                                amountCents = 3499,
                                currency = "EUR",
                                failureReason = "Card declined",
                                createdAt = "2025-01-10T14:30:45.123Z",
                            )
                    }
                }
            }
            code(HttpStatusCode.NotImplemented) {
                description = "This endpoint is not implemented yet."
            }
        }
    }

private fun getPaymentDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getPayment"
        summary = "Get Payment"
        description =
            """
            Retrieve a previously created payment by its ID.

            **This endpoint is not implemented yet.** It documents the intended response contract so
            that calling services can be built and tested against a stable schema.
            """.trimIndent()
        tags = listOf("payments")
        request {
            pathParameter<Uuid>("paymentId") {
                description = "Payment UUID"
                example("Payment details") {
                    value = "660e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The intended response shape once this endpoint is implemented."
                body<PaymentResponse> {
                    example("Payment details") {
                        value =
                            PaymentResponse(
                                paymentId = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                                status = PaymentStatus.Succeeded,
                                orderReference = "550e8400-e29b-41d4-a716-446655440000",
                                amountCents = 3499,
                                currency = "EUR",
                                failureReason = null,
                                createdAt = "2025-01-10T14:30:45.123Z",
                            )
                    }
                }
            }
            code(HttpStatusCode.NotImplemented) {
                description = "This endpoint is not implemented yet."
            }
        }
    }

private fun healthCheckDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "healthCheck"
        summary = "Health Check"
        description =
            """
            Check the payment service health status.
            """.trimIndent()
        tags = listOf("health")
        response {
            code(HttpStatusCode.OK) {
                body<HealthResponse>()
            }
        }
    }
