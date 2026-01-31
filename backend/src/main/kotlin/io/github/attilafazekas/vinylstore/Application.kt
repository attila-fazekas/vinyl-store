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

import com.sun.tools.javac.tree.TreeInfo.args
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.attilafazekas.vinylstore.routes.adminRoutes
import io.github.attilafazekas.vinylstore.routes.healthRoutes
import io.github.attilafazekas.vinylstore.routes.v1.addressRoutes
import io.github.attilafazekas.vinylstore.routes.v1.artistRoutes
import io.github.attilafazekas.vinylstore.routes.v1.authRoutes
import io.github.attilafazekas.vinylstore.routes.v1.genreRoutes
import io.github.attilafazekas.vinylstore.routes.v1.inventoryRoutes
import io.github.attilafazekas.vinylstore.routes.v1.labelRoutes
import io.github.attilafazekas.vinylstore.routes.v1.listingRoutes
import io.github.attilafazekas.vinylstore.routes.v1.userRoutes
import io.github.attilafazekas.vinylstore.routes.v1.vinylRoutes
import io.github.attilafazekas.vinylstore.routes.v2.authV2Routes
import io.github.attilafazekas.vinylstore.routes.v2.inventoryV2Routes
import io.github.attilafazekas.vinylstore.routes.v2.listingV2Routes
import io.github.attilafazekas.vinylstore.routes.v2.vinylV2Routes
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.ExampleEncoder
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

const val USER_AUTH = "UserAuth"
const val V1 = "v1"
const val V2 = "v2"

fun main(args: Array<String>) {
    val autoReset = args.contains("--auto-reset")

    println("Vinyl Store API running on http://localhost:8080")
    println("Admin credentials: admin@vinylstore.com / admin123")
    println("Try: http://localhost:8080/v1/listings")
    startVinylStoreServer(autoReset)
}

fun startVinylStoreServer(autoReset: Boolean = false) =
    embeddedServer(Netty, port = 8080) {
        vinylStoreApplication(autoReset = autoReset)
    }.start(wait = true)

fun Application.vinylStoreApplication(
    store: VinylStoreData = VinylStoreData(),
    autoReset: Boolean = false,
) {
    configureOpenApi()
    configurePlugins()

    // Auto-reset checker - only if enabled
    if (autoReset) {
        intercept(ApplicationCallPipeline.Monitoring) {
            if (store.shouldReset()) {
                store.resetToBootstrap()
            }
        }
    }

    routing {
        route("api.json") {
            openApi()
        }

        route("swagger") {
            swaggerUI(openApiUrl = "/api.json")
        }

        healthRoutes(store, autoReset)
        authRoutes(store)
        authV2Routes(store)
        userRoutes(store)
        addressRoutes(store)
        listingRoutes(store)
        listingV2Routes(store)
        inventoryRoutes(store)
        inventoryV2Routes(store)
        artistRoutes(store)
        genreRoutes(store)
        labelRoutes(store)
        vinylRoutes(store)
        vinylV2Routes(store)
        adminRoutes(store)
    }
}

private fun Application.configureOpenApi() {
    install(OpenApi) {
        schemas {
            // configure the schema generator to use the default kotlinx-serializer
            generator = SchemaGenerator.kotlinx()
        }
        examples {
            // configure the example encoder to encode kotlin objects using kotlinx-serializer
            exampleEncoder = ExampleEncoder.kotlinx()
        }
        outputFormat = OutputFormat.JSON
        info {
            title = "Vinyl Store API"
            version = "1.0.0"
            description =
                """
                A comprehensive REST API for managing a vinyl record store, including catalog management,
                inventory tracking, user accounts, and listings.

                ## Features
                - **Authentication**: JWT-based authentication with role-based access control (CUSTOMER, STAFF, ADMIN)
                - **Catalog Management**: Manage artists, labels, genres, and vinyl records
                - **Listings & Inventory**: Create and manage listings with real-time inventory tracking
                - **User Management**: User registration, authentication, and profile management with addresses
                - **Filtering & Search**: Advanced filtering capabilities across all resources
                - **API Versioning**: Both v1 and v2 endpoints available

                ## Timestamp Format
                All timestamps in the API use ISO 8601 format with UTC timezone:
                ```
                2025-01-10T14:30:45.123Z
                ```
                This format is human-readable, sortable, and widely supported across programming languages.

                ## Authentication
                Most endpoints require authentication via JWT token. Include the token in the Authorization header:
                ```
                Authorization: Bearer <your-token>
                ```

                Default admin credentials:
                - Email: admin@vinylstore.com
                - Password: admin123

                ## Update Requests
                All UPDATE (PUT) endpoints support partial updates. Fields set to null or omitted
                will not be modified. Only explicitly provided fields will be updated.

                ## Data Reset
                The API automatically resets to its bootstrap state every hour. This ensures a consistent
                testing environment. Check the `/health` endpoint to see time until next reset.

                ## API Versions
                - **v1**: Standard responses with separate entities and references by ID
                - **v2**: Enhanced responses with embedded/nested entities for reduced API calls
                """.trimIndent()
        }
        server {
            url = "http://localhost:8080"
            description = "Development Server"
        }
        security {
            securityScheme(USER_AUTH) {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
            }
            defaultSecuritySchemeNames(USER_AUTH)
        }
    }
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(Authentication) {
        jwt(AUTH_JWT) {
            verifier(JwtConfig.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt()
                val email = credential.payload.getClaim("email").asString()
                val role = credential.payload.getClaim("role").asString()
                UserPrincipal(userId, email, role)
            }
        }
    }
}
