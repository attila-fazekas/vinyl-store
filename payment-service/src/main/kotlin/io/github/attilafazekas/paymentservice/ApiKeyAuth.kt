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

import io.github.attilafazekas.common.API_KEY_HEADER
import io.github.attilafazekas.common.UNAUTHORIZED
import io.github.attilafazekas.common.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.response.respond
import java.security.MessageDigest

private val challengeKey: Any = "ApiKeyAuth"

fun AuthenticationConfig.apiKey(
    name: String,
    expectedKey: String,
) {
    register(ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Config(name, expectedKey)))
}

private object ApiKeyPrincipal

private class ApiKeyAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    private val expectedKey = config.expectedKey

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val providedKey = context.call.request.headers[API_KEY_HEADER]
        if (providedKey != null && matches(providedKey, expectedKey)) {
            context.principal(name, ApiKeyPrincipal)
            return
        }
        context.challenge(challengeKey, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(UNAUTHORIZED, "Missing or invalid API key"))
            challenge.complete()
        }
    }

    private fun matches(
        provided: String,
        expected: String,
    ): Boolean = MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())

    class Config(
        name: String,
        val expectedKey: String,
    ) : AuthenticationProvider.Config(name)
}
