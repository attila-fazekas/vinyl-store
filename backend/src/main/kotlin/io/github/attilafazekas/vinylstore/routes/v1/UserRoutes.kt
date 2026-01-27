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
import io.github.attilafazekas.vinylstore.BAD_REQUEST
import io.github.attilafazekas.vinylstore.CONFLICT
import io.github.attilafazekas.vinylstore.Email
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.Password
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.conflictExample
import io.github.attilafazekas.vinylstore.documentation.insufficientPermissionsExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.CreateUserRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.UpdateUserRequest
import io.github.attilafazekas.vinylstore.models.UserResponse
import io.github.attilafazekas.vinylstore.models.UsersResponse
import io.github.attilafazekas.vinylstore.requireRole
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toBooleanStrictOrNull
import kotlin.text.toIntOrNull
import kotlin.text.uppercase

fun Route.userRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/users") {
            get(listUsersDocumentation()) {
                call.requireRole(Role.ADMIN)

                val roleParam = call.parameters["role"]
                val isActiveParam = call.parameters["isActive"]?.toBooleanStrictOrNull()

                var users =
                    store.getAllUsers().map {
                        UserResponse(it.id, it.email, it.role, it.isActive)
                    }

                roleParam?.let { role ->
                    val roleEnum =
                        try {
                            Role.valueOf(role.uppercase())
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    roleEnum?.let {
                        users = users.filter { it.role == roleEnum }
                    }
                }

                isActiveParam?.let { isActive ->
                    users = users.filter { it.isActive == isActive }
                }

                call.respond(UsersResponse(users, users.size))
            }

            get("/{id}", getUserDocumentation()) {
                call.requireRole(Role.ADMIN)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid user ID"))
                    return@get
                }

                val user = store.getUserById(id)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "User not found"))
                } else {
                    call.respond(UserResponse(user.id, user.email, user.role, user.isActive))
                }
            }

            post(createUserDocumentation()) {
                call.requireRole(Role.ADMIN)

                val request = call.receive<CreateUserRequest>()

                val existingUser = store.getUserByEmail(request.email)
                if (existingUser != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "User with this email already exists"),
                    )
                    return@post
                }

                val user = store.createUser(request.email, request.password, request.role)
                call.respond(
                    HttpStatusCode.Created,
                    UserResponse(user.id, user.email, user.role, user.isActive),
                )
            }

            put("/{id}", updateUserDocumentation()) {
                call.requireRole(Role.ADMIN)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid user ID"))
                    return@put
                }

                val request = call.receive<UpdateUserRequest>()
                val updated = store.updateUser(id, request.role, request.isActive)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "User not found"))
                } else {
                    call.respond(UserResponse(updated.id, updated.email, updated.role, updated.isActive))
                }
            }

            delete("/{id}", deleteUserDocumentation()) {
                call.requireRole(Role.ADMIN)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid user ID"))
                    return@delete
                }

                val deleted = store.deleteUser(id)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "User not found"))
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun listUsersDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "List Users"
        description =
            """
            Retrieve a list of all users in the system with optional filtering capabilities.

            Available filters:
            - role: Filter by user role (CUSTOMER, STAFF, ADMIN)
            - isActive: Filter by account active status (true/false)

            Access restrictions:
            - Only ADMIN role can list all users
            - Returns user basic information without sensitive data

            Requires ADMIN authentication via JWT token.
            """.trimIndent()
        tags = listOf("users")
        request {
            queryParameter<String>("role") {
                description = "Filter by role (CUSTOMER, STAFF, ADMIN)"
                required = false
            }
            queryParameter<Boolean>("isActive") {
                description = "Filter by active status"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<UsersResponse> {
                    example("All users") {
                        value =
                            UsersResponse(
                                users =
                                    listOf(
                                        UserResponse(1, Email("admin@vinylstore.com"), Role.ADMIN, true),
                                        UserResponse(2, Email("staff@vinylstore.com"), Role.STAFF, true),
                                        UserResponse(3, Email("john@example.com"), Role.CUSTOMER, false),
                                    ),
                                total = 3,
                            )
                    }
                    example("Empty results") {
                        value =
                            UsersResponse(
                                users = emptyList(),
                                total = 0,
                            )
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can list all users")
        }
    }

private fun getUserDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get User"
        description =
            """
            Retrieve detailed information about a specific user by their ID.

            Returns user information including:
            - User ID
            - Email address
            - Assigned role
            - Account active status

            Access restrictions:
            - Only ADMIN role can view user details

            Requires ADMIN authentication via JWT token.
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "User ID"
                example("User details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<UserResponse> {
                    example("User details") {
                        value = UserResponse(1, Email("john@example.com"), Role.CUSTOMER, true)
                    }
                }
            }
            badRequestExample("Invalid user ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can view user details")
            notFoundExample("User not found")
        }
    }

private fun createUserDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Create User"
        description =
            """
            Create a new user account in the system.

            Fields:
            - email: User's email address (must be unique)
            - password: User's password (must be at least 8 characters)
            - role: User's role in the system (defaults to CUSTOMER)

            The newly created user account will be active by default.

            Access restrictions:
            - Only ADMIN role can create users

            Requires ADMIN authentication via JWT token.
            """.trimIndent()
        tags = listOf("users")
        request {
            body<CreateUserRequest> {
                description = "User creation details"
                example("Create customer") {
                    value =
                        CreateUserRequest(
                            email = Email("customer@example.com"),
                            password = Password("test1234"),
                            role = Role.CUSTOMER,
                        )
                }
                example("Create admin") {
                    value =
                        CreateUserRequest(
                            email = Email("admin@example.com"),
                            password = Password("admin123"),
                            role = Role.ADMIN,
                        )
                }
                example("Create staff member") {
                    value =
                        CreateUserRequest(
                            email = Email("staff@example.com"),
                            password = Password("staff123"),
                            role = Role.STAFF,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<UserResponse> {
                    example("Created user") {
                        value = UserResponse(3, Email("customer@example.com"), Role.CUSTOMER, true)
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can create users")
            conflictExample("Email already exists" to "User with this email already exists")
        }
    }

private fun updateUserDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Update User"
        description =
            """
            Update a user's role or active status with support for partial updates.

            Updatable fields:
            - role: Change user role (CUSTOMER, STAFF, ADMIN)
            - isActive: Activate or deactivate user account

            All fields are optional - only provided fields will be updated. Fields set to null or omitted will remain unchanged.

            Access restrictions:
            - Only ADMIN role can update users

            Requires ADMIN authentication via JWT token.
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "User ID"
                example("Update user") {
                    value = "1"
                }
            }
            body<UpdateUserRequest> {
                description = "All fields are optional. Only provided fields will be updated."
                example("Change role") {
                    value = UpdateUserRequest(role = Role.STAFF)
                }
                example("Deactivate user") {
                    value = UpdateUserRequest(isActive = false)
                }
                example("Update both") {
                    value = UpdateUserRequest(role = Role.STAFF, isActive = false)
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<UserResponse> {
                    example("Updated user") {
                        value = UserResponse(1, Email("john@example.com"), Role.STAFF, true)
                    }
                }
            }
            badRequestExample("Invalid user ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can delete users")
            notFoundExample("User not found")
        }
    }

private fun deleteUserDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Delete User"
        description =
            """
            Permanently delete a user account from the system.

            Warning:
            - This action is irreversible
            - All user data will be permanently removed
            - Consider deactivating the account instead if you need to preserve historical data

            Access restrictions:
            - Only ADMIN role can delete users

            Requires ADMIN authentication via JWT token.
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "User ID"
                example("Delete user") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "User deleted successfully"
            }
            badRequestExample("Invalid user ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN role can update users")
            notFoundExample("User not found")
        }
    }
