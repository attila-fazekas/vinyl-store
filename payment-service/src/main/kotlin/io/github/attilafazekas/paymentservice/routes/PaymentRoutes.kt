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

import io.github.attilafazekas.paymentservice.AUTH_API_KEY
import io.github.attilafazekas.paymentservice.BAD_REQUEST
import io.github.attilafazekas.paymentservice.NOT_FOUND
import io.github.attilafazekas.paymentservice.PaymentRepository
import io.github.attilafazekas.paymentservice.documentation.badRequestExample
import io.github.attilafazekas.paymentservice.documentation.notAuthenticatedExample
import io.github.attilafazekas.paymentservice.documentation.notFoundExample
import io.github.attilafazekas.paymentservice.enums.PaymentStatus
import io.github.attilafazekas.paymentservice.models.ErrorResponse
import io.github.attilafazekas.paymentservice.models.PaymentRequest
import io.github.attilafazekas.paymentservice.models.PaymentResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.uuid.Uuid

fun Route.paymentRoutes(paymentRepo: PaymentRepository) {
    authenticate(AUTH_API_KEY) {
        post("/payments", chargePaymentDocumentation()) {
            val request = call.receive<PaymentRequest>()
            call.respond(HttpStatusCode.OK, paymentRepo.charge(request))
        }

        get("/payments/{paymentId}", getPaymentDocumentation()) {
            val paymentId = call.parameters["paymentId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            if (paymentId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid payment ID"))
                return@get
            }

            val payment = paymentRepo.getById(paymentId)
            if (payment == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Payment not found"))
            } else {
                call.respond(payment)
            }
        }
    }
}

private fun chargePaymentDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "chargePayment"
        summary = "Charge Payment"
        description =
            """
            Charge a payment method for a given order.

            **Simulated Behavior:**
            - Charges are synchronous: the response reflects the final outcome (Succeeded or Failed)
            - Retrying a charge with the same idempotencyKey replays the original response instead of charging again
            - The paymentMethod `tok_chargeDeclined` simulates a declined card; any other value succeeds
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
            notAuthenticatedExample()
        }
    }

private fun getPaymentDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getPayment"
        summary = "Get Payment"
        description =
            """
            Retrieve a previously created payment by its ID.
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
            badRequestExample("Invalid payment ID")
            notAuthenticatedExample()
            notFoundExample("Payment not found")
        }
    }
