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

package io.github.attilafazekas.vinylstore.routes.v2

import io.github.attilafazekas.vinylstore.AUTH_JWT
import io.github.attilafazekas.vinylstore.Email
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V2
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Address
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.attilafazekas.vinylstore.models.UserStatsV2
import io.github.attilafazekas.vinylstore.models.UserV2Response
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.authV2Routes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V2/auth") {
            get("/me", getCurrentUserWithDetailsDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val user = store.getUserById(principal.userId)!!
                val addresses = store.getAddressesByUserId(user.id)

                val totalOrders = 0 // TODO: Implement order counting when orders are added

                val response =
                    UserV2Response(
                        id = user.id,
                        email = user.email,
                        role = user.role,
                        isActive = user.isActive,
                        addresses = addresses,
                        stats =
                            UserStatsV2(
                                totalOrders = totalOrders,
                                accountCreated = user.createdAt,
                            ),
                    )

                call.respond(response)
            }
        }
    }
}

private fun getCurrentUserWithDetailsDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Current User with Details (V2)"
        description =
            """
            Retrieve comprehensive information about the currently authenticated user with all related data embedded.

            This V2 endpoint provides enhanced response structure including:
            - Basic user profile (ID, email, role, active status)
            - All associated addresses (shipping and billing)
            - User statistics (total orders, account creation date)

            This endpoint reduces the need for multiple API calls by embedding related data directly in the response.
            Requires a valid JWT token in the Authorization header.
            """.trimIndent()
        tags = listOf("auth-v2")
        response {
            code(HttpStatusCode.OK) {
                body<UserV2Response> {
                    example("Customer user with addresses") {
                        value =
                            UserV2Response(
                                id = 1,
                                email = Email("john@example.com"),
                                role = Role.CUSTOMER,
                                isActive = true,
                                addresses =
                                    listOf(
                                        Address(
                                            id = 1,
                                            userId = 1,
                                            type = AddressType.SHIPPING,
                                            fullName = "John Doe",
                                            street = "123 Main St",
                                            city = "New York",
                                            postalCode = "10001",
                                            country = "USA",
                                            isDefault = true,
                                            createdAt = TimestampUtil.now(),
                                            updatedAt = TimestampUtil.now(),
                                        ),
                                    ),
                                stats =
                                    UserStatsV2(
                                        totalOrders = 5,
                                        accountCreated = "2025-01-10T14:30:45.123Z",
                                    ),
                            )
                    }
                    example("Admin user") {
                        value =
                            UserV2Response(
                                id = 2,
                                email = Email("admin@vinylstore.com"),
                                role = Role.ADMIN,
                                isActive = true,
                                addresses = emptyList(),
                                stats =
                                    UserStatsV2(
                                        totalOrders = 0,
                                        accountCreated = "2025-01-10T14:30:45.123Z",
                                    ),
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }
