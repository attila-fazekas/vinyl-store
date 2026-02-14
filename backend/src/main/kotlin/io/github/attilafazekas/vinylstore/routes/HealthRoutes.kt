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

import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.models.HealthResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.healthRoutes(
    store: VinylStoreData,
    autoReset: Boolean = false,
) {
    get("/health", healthCheckDocumentation()) {
        val uptime = System.currentTimeMillis() - store.createdAt
        val nextReset =
            if (autoReset) {
                val oneHour = 60 * 60 * 1000
                formatDuration(oneHour - uptime)
            } else {
                null
            }
        call.respond(HealthResponse("OK", formatDuration(uptime), nextReset))
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = (milliseconds / (1000 * 60 * 60))
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

private fun healthCheckDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "healthCheck"
        summary = "Health Check"
        description =
            """
            Check the API health status and retrieve uptime information.
            """.trimIndent()
        tags = listOf("health")
        response {
            code(HttpStatusCode.OK) {
                body<HealthResponse>()
            }
        }
    }
