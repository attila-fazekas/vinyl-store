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
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VALIDATION_ERROR
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.documentation.validationErrorExample
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.models.Address
import io.github.attilafazekas.vinylstore.models.AddressesResponse
import io.github.attilafazekas.vinylstore.models.CreateAddressRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.UpdateAddressRequest
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toBooleanStrictOrNull
import kotlin.text.toIntOrNull
import kotlin.text.uppercase

fun Route.addressRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/users/me/addresses") {
            get(listAddressesDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val typeParam = call.parameters["type"]
                val isDefaultParam = call.parameters["isDefault"]?.toBooleanStrictOrNull()

                var addresses = store.getAddressesByUserId(principal.userId)

                typeParam?.let { type ->
                    val addressType =
                        try {
                            AddressType.valueOf(type.uppercase())
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    addressType?.let {
                        addresses = addresses.filter { address -> address.type == addressType }
                    }
                }

                isDefaultParam?.let { isDefault ->
                    addresses = addresses.filter { address -> address.isDefault == isDefault }
                }

                call.respond(AddressesResponse(addresses, addresses.size))
            }

            get("/{id}", getAddressDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid address ID"))
                    return@get
                }

                val address = store.getAddressById(id)

                if (address == null || address.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Address not found"))
                    return@get
                }

                call.respond(address)
            }

            post(createAddressDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val request = call.receive<CreateAddressRequest>()

                if (request.fullName.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Full name is required"),
                    )
                    return@post
                }

                if (request.street.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Street is required"),
                    )
                    return@post
                }

                if (request.city.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "City is required"),
                    )
                    return@post
                }

                if (request.postalCode.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Postal code is required"),
                    )
                    return@post
                }

                if (request.country.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Country is required"),
                    )
                    return@post
                }

                val address =
                    store.createAddress(
                        principal.userId,
                        request.type,
                        request.fullName,
                        request.street,
                        request.city,
                        request.postalCode,
                        request.country,
                        request.isDefault,
                    )

                call.respond(HttpStatusCode.Created, address)
            }

            put("/{id}", updateAddressDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid address ID"))
                    return@put
                }

                val existingAddress = store.getAddressById(id)

                if (existingAddress == null || existingAddress.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Address not found"))
                    return@put
                }

                val request = call.receive<UpdateAddressRequest>()

                // If making this address default, unset other defaults for the same type
                if (request.isDefault == true) {
                    val addressType = request.type ?: existingAddress.type
                    store.unsetDefaultAddresses(principal.userId, addressType)
                }

                val updated =
                    store.updateAddress(
                        id,
                        request.type,
                        request.fullName,
                        request.street,
                        request.city,
                        request.postalCode,
                        request.country,
                        request.isDefault,
                    )

                call.respond(updated!!)
            }

            delete("/{id}", deleteAddressDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid address ID"))
                    return@delete
                }

                val address = store.getAddressById(id)

                if (address == null || address.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Address not found"))
                    return@delete
                }

                store.deleteAddress(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun listAddressesDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "listAddresses"
        summary = "List My Addresses"
        description =
            """
            Retrieve all shipping and billing addresses for the currently authenticated user.

            Available filters:
            - type: Filter by address type (SHIPPING or BILLING)
            - isDefault: Filter for default addresses only

            Each address includes:
            - Address ID and type
            - Full name, street, city, postal code, country
            - Default address indicator

            Users can only access their own addresses.
            """.trimIndent()
        tags = listOf("users")
        request {
            queryParameter<String>("type") {
                description = "Filter by address type (SHIPPING or BILLING)"
                required = false
            }
            queryParameter<Boolean>("isDefault") {
                description = "Filter for default addresses only"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<AddressesResponse> {
                    example("User addresses") {
                        value =
                            AddressesResponse(
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
                                total = 1,
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun getAddressDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getAddress"
        summary = "Get Address"
        description =
            """
            Retrieve details of a specific address by its ID.

            Returns complete address information including:
            - Address type (SHIPPING or BILLING)
            - Recipient full name
            - Complete street address
            - City, postal code, and country
            - Default address indicator

            Access control:
            - Users can only access their own addresses
            - Returns 404 for non-existent addresses or addresses belonging to other users
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "Address ID"
                example("Address details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Address> {
                    example("Address details") {
                        value =
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
                            )
                    }
                }
            }
            badRequestExample("Invalid address ID")
            notAuthenticatedExample()
            notFoundExample("Address not found")
        }
    }

private fun createAddressDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "createAddress"
        summary = "Create Address"
        description =
            """
            Create a new shipping or billing address for the currently authenticated user.

            Address requirements:
            - Full name is required
            - Street address is required
            - City is required
            - Postal code is required
            - Country is required
            - Address type must be SHIPPING or BILLING

            Default address behavior:
            - If setting this address as default, any existing default address of the same type will be automatically unmarked
            - Only one default address per type (SHIPPING or BILLING) is allowed
            - The system automatically handles this without returning conflicts
            """.trimIndent()
        tags = listOf("users")
        request {
            body<CreateAddressRequest> {
                description = "Address details for creating a new shipping or billing address."
                example("Shipping address") {
                    value =
                        CreateAddressRequest(
                            type = AddressType.SHIPPING,
                            fullName = "John Doe",
                            street = "123 Main St",
                            city = "New York",
                            postalCode = "10001",
                            country = "USA",
                            isDefault = true,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Address> {
                    example("Created address") {
                        value =
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
                            )
                    }
                }
            }
            validationErrorExample(
                "Invalid address data",
                "Full name is required",
                "Street is required",
                "City is required",
                "Postal code is required",
                "Country is required",
            )
            notAuthenticatedExample()
        }
    }

private fun updateAddressDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "updateAddress"
        summary = "Update Address"
        description =
            """
            Update an existing address with support for partial updates.

            Updatable fields:
            - type: Change address type (SHIPPING or BILLING)
            - fullName: Update recipient name
            - street, city, postalCode, country: Update address components
            - isDefault: Set or unset as default address

            All fields are optional - only provided fields will be updated.
            If setting as default, any existing default address of the same type will be automatically unmarked.

            Access control:
            - Users can only update their own addresses
            - Returns 404 for non-existent addresses or addresses belonging to other users
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "Address ID"
                example("Update address") {
                    value = "1"
                }
            }
            body<UpdateAddressRequest> {
                description = "All fields are optional. Only provided fields will be updated."
                example("Update street") {
                    value = UpdateAddressRequest(street = "456 Oak Ave")
                }
                example("Make default") {
                    value = UpdateAddressRequest(isDefault = true)
                }
                example("Update multiple fields") {
                    value =
                        UpdateAddressRequest(
                            street = "789 Pine Rd",
                            city = "Los Angeles",
                            postalCode = "90001",
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Address> {
                    example("Updated address") {
                        value =
                            Address(
                                id = 1,
                                userId = 1,
                                type = AddressType.SHIPPING,
                                fullName = "John Doe",
                                street = "456 Oak Ave",
                                city = "New York",
                                postalCode = "10001",
                                country = "USA",
                                isDefault = true,
                                createdAt = TimestampUtil.now(),
                                updatedAt = TimestampUtil.now(),
                            )
                    }
                }
            }
            badRequestExample("Invalid address ID")
            notAuthenticatedExample()
            notFoundExample("Address not found")
        }
    }

private fun deleteAddressDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "deleteAddress"
        summary = "Delete Address"
        description =
            """
            Permanently delete an address from the current user's account.

            Notes:
            - The deletion is immediate and permanent
            - If deleting a default address, remember to set another address as default

            Access control:
            - Users can only delete their own addresses
            - Returns 404 for non-existent addresses or addresses belonging to other users
            """.trimIndent()
        tags = listOf("users")
        request {
            pathParameter<String>("id") {
                description = "Address ID"
                example("Delete address") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Address deleted successfully"
            }
            badRequestExample("Invalid address ID")
            notAuthenticatedExample()
            notFoundExample("Address not found")
        }
    }
