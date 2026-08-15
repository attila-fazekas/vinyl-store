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

import io.github.attilafazekas.common.formatDuration
import io.github.attilafazekas.paymentservice.models.HealthResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

private val serviceStartedAt = System.currentTimeMillis()

fun Route.healthRoutes() {
    get("/health", healthCheckDocumentation()) {
        val uptime = System.currentTimeMillis() - serviceStartedAt
        call.respond(HealthResponse(status = "OK", uptime = formatDuration(uptime)))
    }
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
