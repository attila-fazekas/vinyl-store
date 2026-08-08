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

import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.CreateUserRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.LoginRequest
import io.github.attilafazekas.vinylstore.models.RegisterRequest
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.komapper.r2dbc.R2dbcDatabase
import kotlin.uuid.Uuid

class AuthRoutesValidationTest {
    @Test
    fun `Register user rejects empty password`() {
        withAuthValidationFixture { client, _ ->
            val response =
                client.post("/v1/auth/register") {
                    setBody(
                        RegisterRequest(
                            email = Email("customer-${Uuid.random()}@example.com"),
                            password = Password(""),
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorResponse>() shouldBe passwordValidationError()
        }
    }

    @Test
    fun `Login user rejects empty password as invalid credentials`() {
        withAuthValidationFixture { client, _ ->
            val response =
                client.post("/v1/auth/login") {
                    setBody(
                        LoginRequest(
                            email = Email(CUSTOMER_EMAIL),
                            password = Password(""),
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ErrorResponse>() shouldBe invalidCredentialsError()
        }
    }

    @Test
    fun `Create user rejects empty password`() {
        withAuthValidationFixture { client, adminToken ->
            val response =
                client.post("/v1/users") {
                    header(HttpHeaders.Authorization, "Bearer $adminToken")
                    setBody(
                        CreateUserRequest(
                            email = Email("customer-${Uuid.random()}@example.com"),
                            password = Password(""),
                            role = Role.Customer,
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorResponse>() shouldBe passwordValidationError()
        }
    }

    private fun withAuthValidationFixture(block: suspend (HttpClient, String) -> Unit) {
        testApplication {
            val store =
                VinylStoreRepository(
                    R2dbcDatabase("r2dbc:postgresql://vinylstore:vinylstore@localhost/vinylstore"),
                )
            application { vinylStoreApplication(store = store) }

            val admin =
                store.createUser(
                    Email("admin-${Uuid.random()}@example.com"),
                    Password("password123"),
                    Role.Admin,
                )
            val adminToken = JwtConfig.generateToken(admin.id, admin.email, admin.role)
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    install(DefaultRequest) {
                        contentType(ContentType.Application.Json)
                    }
                }

            block(client, adminToken)
        }
    }

    private fun passwordValidationError(): ErrorResponse = ErrorResponse(VALIDATION_ERROR, "Password must be at least 8 characters")

    private fun invalidCredentialsError(): ErrorResponse = ErrorResponse(UNAUTHORIZED, "Invalid credentials")
}
