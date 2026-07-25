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

import io.github.attilafazekas.paymentservice.routes.paymentRoutes
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

fun Application.paymentServiceApplication() {
    configureOpenApi()
    configurePlugins()

    routing {
        route("api.json") {
            openApi()
        }

        route("swagger") {
            swaggerUI(openApiUrl = "/api.json")
        }

        paymentRoutes()
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
                A contract-only payment processing API modeling how vinylstore integrates with an
                external payment provider.

                ## Status
                This service is not implemented yet. Every endpoint documents its intended request and
                response shape, but handlers currently respond with 501 Not Implemented. The published
                contract is intended to be stubbed with WireMock by calling services during testing.
                """.trimIndent()
        }
        server {
            url = "http://localhost:9090"
            description = "Development Server"
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
