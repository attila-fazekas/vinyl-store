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

package io.github.attilafazekas.vinylstore.routes.v1

import io.github.attilafazekas.vinylstore.AUTH_JWT
import io.github.attilafazekas.vinylstore.CONFLICT
import io.github.attilafazekas.vinylstore.Email
import io.github.attilafazekas.vinylstore.FORBIDDEN
import io.github.attilafazekas.vinylstore.JwtConfig
import io.github.attilafazekas.vinylstore.Password
import io.github.attilafazekas.vinylstore.PasswordUtil
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.UNAUTHORIZED
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VALIDATION_ERROR
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.conflictExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.validationErrorExample
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.LoginRequest
import io.github.attilafazekas.vinylstore.models.LoginResponse
import io.github.attilafazekas.vinylstore.models.RegisterRequest
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.attilafazekas.vinylstore.models.UserResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.authRoutes(store: VinylStoreData) {
    route("$V1/auth") {
        post("/register", registerUserDocumentation()) {
            val request = call.receive<RegisterRequest>()

            if (request.email.value.isBlank() || !request.email.value.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(VALIDATION_ERROR, "Invalid email"))
                return@post
            }

            if (request.password.value.length < 8) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(VALIDATION_ERROR, "Password must be at least 8 characters"),
                )
                return@post
            }

            if (store.getUserByEmail(request.email) != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(CONFLICT, "Email already exists"))
                return@post
            }

            val user = store.createUser(request.email, request.password, Role.CUSTOMER)
            val token = JwtConfig.generateToken(user.id, user.email, user.role)

            call.respond(
                HttpStatusCode.Created,
                LoginResponse(
                    token = token,
                    user =
                        UserResponse(
                            id = user.id,
                            email = user.email,
                            role = user.role,
                            isActive = user.isActive,
                            createdAt = user.createdAt,
                            updatedAt = user.updatedAt,
                        ),
                ),
            )
        }

        post("/login", loginDocumentation()) {
            val request = call.receive<LoginRequest>()
            val user = store.getUserByEmail(request.email)

            if (user == null || !PasswordUtil.verify(request.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(UNAUTHORIZED, "Invalid credentials"))
                return@post
            }

            if (!user.isActive) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(FORBIDDEN, "Account is not active"))
                return@post
            }

            val token = JwtConfig.generateToken(user.id, user.email, user.role)
            call.respond(
                LoginResponse(
                    token,
                    UserResponse(
                        id = user.id,
                        email = user.email,
                        role = user.role,
                        isActive = true,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt,
                    ),
                ),
            )
        }

        authenticate(AUTH_JWT) {
            get("/me", getCurrentUserDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val user = store.getUserById(principal.userId)!!
                call.respond(
                    UserResponse(
                        id = user.id,
                        email = user.email,
                        role = user.role,
                        isActive = user.isActive,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt,
                    ),
                )
            }
        }
    }
}

private fun getCurrentUserDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getCurrentUser"
        summary = "Get Current User"
        description =
            """
            Retrieve information about the currently authenticated user.

            Returns basic user profile information including:
            - User ID
            - Email address
            - Assigned role (CUSTOMER, STAFF, or ADMIN)
            - Account active status

            Requires a valid JWT token in the Authorization header.
            """.trimIndent()
        tags = listOf("auth")
        response {
            code(HttpStatusCode.OK) {
                body<UserResponse> {
                    example("Customer user") {
                        value =
                            UserResponse(
                                1,
                                Email("john@example.com"),
                                Role.CUSTOMER,
                                true,
                                TimestampUtil.now(),
                                TimestampUtil.now(),
                            )
                    }
                    example("Admin user") {
                        value =
                            UserResponse(
                                2,
                                Email("admin@vinylstore.com"),
                                Role.ADMIN,
                                true,
                                TimestampUtil.now(),
                                TimestampUtil.now(),
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun registerUserDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "registerUser"
        summary = "Register User"
        description =
            """
            Create a new user account with email and password credentials.

            Registration requirements:
            - Email must be unique and contain an '@' symbol
            - Password must be at least 8 characters long
            - New users are automatically assigned the CUSTOMER role
            - Account is active by default

            Upon successful registration, returns a JWT token for immediate authentication along with the user details.
            """.trimIndent()
        tags = listOf("auth")
        request {
            body<RegisterRequest> {
                description =
                    "User registration credentials. Email must be valid and password must be at least 8 characters."
                example("Basic") {
                    value =
                        RegisterRequest(
                            email = Email("john@example.com"),
                            password = Password("test1234"),
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<LoginResponse> {
                    example("Successful registration") {
                        value =
                            LoginResponse(
                                token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                user =
                                    UserResponse(
                                        id = 2,
                                        email = Email("john@example.com"),
                                        role = Role.CUSTOMER,
                                        isActive = true,
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                            )
                    }
                }
            }
            validationErrorExample("Invalid email")
            conflictExample("Email already exists" to "Email is already registered")
        }
    }

private fun loginDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "login"
        summary = "User Login"
        description =
            """
            Authenticate a user with email and password credentials.

            The authentication process:
            - Validates provided email and password against stored credentials
            - Verifies that the user account is active
            - Generates a JWT token for subsequent API requests

            The returned JWT token must be included in the Authorization header as "Bearer <token>" for protected endpoints.
            Use the default admin credentials (admin@vinylstore.com / admin123) for admin access.
            """.trimIndent()
        tags = listOf("auth")
        request {
            body<LoginRequest> {
                description = "User login credentials."
                example("Basic") {
                    value =
                        LoginRequest(
                            email = Email("john@example.com"),
                            password = Password("test1234"),
                        )
                }
                example("Admin") {
                    value =
                        LoginRequest(
                            email = Email("admin@vinylstore.com"),
                            password = Password("admin123"),
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<LoginResponse> {
                    example("Successful login") {
                        value =
                            LoginResponse(
                                token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                user =
                                    UserResponse(
                                        1,
                                        Email("john@example.com"),
                                        Role.CUSTOMER,
                                        true,
                                        TimestampUtil.now(),
                                        TimestampUtil.now(),
                                    ),
                            )
                    }
                }
            }
            code(HttpStatusCode.Unauthorized) {
                body<ErrorResponse> {
                    example("Invalid credentials") {
                        value = ErrorResponse(UNAUTHORIZED, "Invalid credentials")
                    }
                }
            }
            code(HttpStatusCode.Forbidden) {
                body<ErrorResponse> {
                    example("Account not active") {
                        value = ErrorResponse(FORBIDDEN, "Account is not active")
                    }
                }
            }
        }
    }
