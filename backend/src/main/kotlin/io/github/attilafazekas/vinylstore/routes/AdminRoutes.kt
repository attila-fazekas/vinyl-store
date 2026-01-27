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

package io.github.attilafazekas.vinylstore.routes

import io.github.attilafazekas.vinylstore.AUTH_JWT
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.insufficientPermissionsExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.MessageResponse
import io.github.attilafazekas.vinylstore.requireRole
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.adminRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        post("/admin/reset", adminDocumentation()) {
            call.requireRole(Role.ADMIN)
            store.resetToBootstrap()
            call.respond(MessageResponse("Data reset to bootstrap state"))
        }
    }
}

private fun adminDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Reset Data"
        description =
            """
            Reset all data to the initial bootstrap state for testing and development purposes.

            **What Gets Reset:**
            - All users (except admin user is recreated)
            - All vinyls, artists, labels, and genres
            - All listings and inventory
            - All addresses and user data
            - Resets to predefined bootstrap data

            **Bootstrap Data:**
            After reset, the system contains:
            - Default admin user (admin@vinylstore.com / admin123)
            - Sample artists, labels, and genres
            - Sample vinyl records
            - Sample listings with inventory

            **Automatic Reset:**
            The API automatically resets every hour to maintain a consistent testing environment.
            Check the /health endpoint for time until next automatic reset.

            **Use Cases:**
            - Restore system to known state for testing
            - Clear test data during development
            - Reset demo environment
            - Recover from data corruption

            **Warning:**
            This action is immediate and irreversible. All current data will be permanently lost.

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN role can reset data
            - Returns 403 Forbidden for STAFF and CUSTOMER roles
            """.trimIndent()
        tags = listOf("admin")
        response {
            code(HttpStatusCode.OK) {
                body<MessageResponse> {
                    example("Reset successful") {
                        value = MessageResponse("Data reset to bootstrap state")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can reset data")
        }
    }
