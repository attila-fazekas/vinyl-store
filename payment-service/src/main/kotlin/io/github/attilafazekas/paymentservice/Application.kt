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

import io.github.attilafazekas.paymentservice.routes.healthRoutes
import io.github.attilafazekas.paymentservice.routes.paymentRoutes
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthKeyLocation
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    println("Payment Service API running on http://localhost:9090")
    println("Swagger UI: http://localhost:9090/swagger")
    startPaymentServiceServer()
}

fun startPaymentServiceServer() =
    embeddedServer(Netty, port = 9090, watchPaths = listOf("classes")) {
        paymentServiceApplication()
    }.start(wait = true)

fun Application.paymentServiceApplication(paymentRepo: PaymentRepository = PaymentRepository()) {
    configureOpenApi()
    configurePlugins()
    configureAuthentication()

    routing {
        route("api.json") {
            openApi()
        }

        route("swagger") {
            swaggerUI(openApiUrl = "/api.json")
        }

        paymentRoutes(paymentRepo)
        healthRoutes()
    }
}

private fun Application.configureOpenApi() {
    install(OpenApi) {
        outputFormat = OutputFormat.JSON
        info {
            title = "Payment Service API"
            version = "1.0.0"
            description =
                """
                A payment processing API that simulates an external payment provider, modeling how
                vinylstore integrates with one for testing purposes.

                ## Status
                Charges are processed synchronously against an in-memory simulated processor (not a real
                payment gateway). The paymentMethod `tok_chargeDeclined` simulates a declined card; any
                other value succeeds. Retrying a charge with the same idempotencyKey replays the original
                response instead of charging again.

                ## Authentication
                All endpoints (except `/health`) require an `X-API-Key` header.
                """.trimIndent()
        }
        server {
            url = "http://localhost:9090"
            description = "Development Server"
        }
        security {
            securityScheme(AUTH_API_KEY) {
                type = AuthType.API_KEY
                location = AuthKeyLocation.HEADER
                name = API_KEY_HEADER
            }
            defaultSecuritySchemeNames(AUTH_API_KEY)
        }
    }
}

private fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            },
        )
    }
}

private fun Application.configureAuthentication() {
    val expectedApiKey = System.getenv("PAYMENT_SERVICE_API_KEY") ?: DEFAULT_PAYMENT_SERVICE_API_KEY
    install(Authentication) {
        apiKey(AUTH_API_KEY, expectedApiKey)
    }
}
