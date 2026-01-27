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

package io.github.attilafazekas.vinylstore.documentation

import io.github.attilafazekas.vinylstore.BAD_REQUEST
import io.github.attilafazekas.vinylstore.CONFLICT
import io.github.attilafazekas.vinylstore.FORBIDDEN
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.UNAUTHORIZED
import io.github.attilafazekas.vinylstore.VALIDATION_ERROR
import io.github.attilafazekas.vinylstore.models.ErrorResponse
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

fun ResponsesConfig.validationErrorExample(vararg message: String) {
    code(HttpStatusCode.BadRequest) {
        body<ErrorResponse> {
            message.forEach { message ->
                example(message) {
                    value = ErrorResponse(VALIDATION_ERROR, message)
                }
            }
        }
    }
}

fun ResponsesConfig.notAuthenticatedExample() {
    code(HttpStatusCode.Unauthorized) {
        body<ErrorResponse> {
            example("Not authenticated") {
                value = ErrorResponse(UNAUTHORIZED, "Not authenticated")
            }
        }
    }
}

fun ResponsesConfig.insufficientPermissionsExample(message: String) {
    code(HttpStatusCode.Forbidden) {
        body<ErrorResponse> {
            example("Insufficient permissions") {
                value = ErrorResponse(FORBIDDEN, message)
            }
        }
    }
}

fun ResponsesConfig.notFoundExample(vararg message: String) {
    code(HttpStatusCode.NotFound) {
        body<ErrorResponse> {
            message.forEach { message ->
                example(message) {
                    value = ErrorResponse(NOT_FOUND, message)
                }
            }
        }
    }
}

fun ResponsesConfig.conflictExample(vararg examples: Pair<String, String>) {
    code(HttpStatusCode.Conflict) {
        body<ErrorResponse> {
            examples.forEach { pair ->
                val (exampleName, errorMessage) = pair
                example(exampleName) {
                    value = ErrorResponse(CONFLICT, errorMessage)
                }
            }
        }
    }
}
