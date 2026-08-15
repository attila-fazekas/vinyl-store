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

package io.github.attilafazekas.paymentservice.documentation

import io.github.attilafazekas.paymentservice.BAD_REQUEST
import io.github.attilafazekas.paymentservice.NOT_FOUND
import io.github.attilafazekas.paymentservice.UNAUTHORIZED
import io.github.attilafazekas.paymentservice.models.ErrorResponse
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.ktor.http.HttpStatusCode

fun ResponsesConfig.badRequestExample(message: String) {
    code(HttpStatusCode.BadRequest) {
        body<ErrorResponse> {
            example(message) {
                value = ErrorResponse(BAD_REQUEST, message)
            }
        }
    }
}

fun ResponsesConfig.notAuthenticatedExample() {
    code(HttpStatusCode.Unauthorized) {
        body<ErrorResponse> {
            example("Missing or invalid API key") {
                value = ErrorResponse(UNAUTHORIZED, "Missing or invalid API key")
            }
        }
    }
}

fun ResponsesConfig.notFoundExample(message: String) {
    code(HttpStatusCode.NotFound) {
        body<ErrorResponse> {
            example(message) {
                value = ErrorResponse(NOT_FOUND, message)
            }
        }
    }
}
