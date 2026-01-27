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
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VALIDATION_ERROR
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.conflictExample
import io.github.attilafazekas.vinylstore.documentation.insufficientPermissionsExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Inventory
import io.github.attilafazekas.vinylstore.models.InventoryResponse
import io.github.attilafazekas.vinylstore.models.UpdateInventoryRequest
import io.github.attilafazekas.vinylstore.models.VinylsResponse
import io.github.attilafazekas.vinylstore.requireRole
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toIntOrNull
import kotlin.text.uppercase

fun Route.inventoryRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/inventory") {
            get(listInventoryDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val minAvailableParam = call.parameters["minAvailable"]?.toIntOrNull()
                val maxAvailableParam = call.parameters["maxAvailable"]?.toIntOrNull()
                val minTotalParam = call.parameters["minTotal"]?.toIntOrNull()
                val listingStatusParam = call.parameters["listingStatus"]

                var inventoryItems = store.inventory.values.sortedBy { it.id }

                minAvailableParam?.let { minAvailable ->
                    inventoryItems = inventoryItems.filter { it.availableQuantity >= minAvailable }
                }

                maxAvailableParam?.let { maxAvailable ->
                    inventoryItems = inventoryItems.filter { it.availableQuantity <= maxAvailable }
                }

                minTotalParam?.let { minTotal ->
                    inventoryItems = inventoryItems.filter { it.totalQuantity >= minTotal }
                }

                listingStatusParam?.let { status ->
                    val statusEnum =
                        try {
                            ListingStatus.valueOf(status.uppercase())
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    statusEnum?.let {
                        inventoryItems =
                            inventoryItems.filter { inv ->
                                val listing = store.listings[inv.listingId]
                                listing?.status == statusEnum
                            }
                    }
                }

                call.respond(InventoryResponse(inventoryItems, inventoryItems.size))
            }

            get("/{listingId}", getInventoryDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val listingId = call.parameters["listingId"]?.toIntOrNull()
                if (listingId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@get
                }

                val inv = store.getInventoryByListingId(listingId)
                if (inv == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Inventory not found"))
                } else {
                    call.respond(inv)
                }
            }

            put("/{listingId}", updateInventoryDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val listingId = call.parameters["listingId"]?.toIntOrNull()
                if (listingId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@put
                }

                val request = call.receive<UpdateInventoryRequest>()

                // Get current inventory to check constraints
                val currentInventory = store.getInventoryByListingId(listingId)
                if (currentInventory == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Inventory not found"))
                    return@put
                }

                if (request.totalQuantity != null && request.totalQuantity < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Total quantity cannot be negative"),
                    )
                    return@put
                }

                if (request.reservedQuantity != null && request.reservedQuantity < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(VALIDATION_ERROR, "Reserved quantity cannot be negative"),
                    )
                    return@put
                }

                val newTotalQuantity = request.totalQuantity ?: currentInventory.totalQuantity
                val newReservedQuantity = request.reservedQuantity ?: currentInventory.reservedQuantity

                if (newReservedQuantity > newTotalQuantity) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Reserved quantity cannot exceed total quantity"),
                    )
                    return@put
                }

                if (request.totalQuantity != null && request.totalQuantity < currentInventory.reservedQuantity) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Cannot set total quantity below current reserved quantity"),
                    )
                    return@put
                }

                val updated = store.updateInventory(listingId, request.totalQuantity, request.reservedQuantity)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Inventory not found"))
                } else {
                    call.respond(updated)
                }
            }
        }
    }
}

private fun listInventoryDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "List Inventory"
        description =
            """
            Retrieve all inventory records with stock management filtering capabilities.

            **Filtering Options:**
            - **minAvailable**: Filter by minimum available quantity (totalQuantity - reservedQuantity)
            - **maxAvailable**: Filter by maximum available quantity
            - **minTotal**: Filter by minimum total quantity
            - **listingStatus**: Filter by associated listing status (DRAFT, PUBLISHED, ARCHIVED)

            **Stock Management Features:**
            - Track total quantity in warehouse
            - Monitor reserved quantity from pending orders
            - Calculate available quantity automatically (total - reserved)
            - Identify low-stock items for reordering

            **Use Cases:**
            - Monitor stock levels across all listings
            - Identify items requiring restocking
            - Filter out-of-stock or low-stock items
            - Generate inventory reports

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can view inventory
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("inventory")
        request {
            queryParameter<Int>("minAvailable") {
                description = "Filter by minimum available quantity"
                required = false
            }
            queryParameter<Int>("maxAvailable") {
                description = "Filter by maximum available quantity"
                required = false
            }
            queryParameter<Int>("minTotal") {
                description = "Filter by minimum total quantity"
                required = false
            }
            queryParameter<String>("listingStatus") {
                description = "Filter by listing status (DRAFT, PUBLISHED, ARCHIVED)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<InventoryResponse> {
                    example("Inventory list") {
                        value =
                            InventoryResponse(
                                inventory =
                                    listOf(
                                        Inventory(
                                            id = 1,
                                            listingId = 1,
                                            totalQuantity = 15,
                                            reservedQuantity = 0,
                                        ),
                                        Inventory(
                                            id = 2,
                                            listingId = 2,
                                            totalQuantity = 5,
                                            reservedQuantity = 2,
                                        ),
                                    ),
                                total = 2,
                            )
                    }
                    example("Empty results") {
                        value =
                            VinylsResponse(
                                vinyls = emptyList(),
                                total = 0,
                            )
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can view inventory")
        }
    }

private fun getInventoryDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Inventory"
        description =
            """
            Retrieve inventory details for a specific listing by listing ID.

            **Returns:**
            - Inventory ID and associated listing ID
            - Total quantity in warehouse
            - Reserved quantity from pending orders
            - Available quantity (calculated: total - reserved)

            **Stock Tracking:**
            The inventory system tracks three key quantities:
            - **totalQuantity**: Physical stock in warehouse
            - **reservedQuantity**: Items allocated to pending orders
            - **availableQuantity**: Items available for new orders

            **Use Cases:**
            - Check real-time stock availability
            - Verify order fulfillment capacity
            - Monitor reserved vs available stock

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can view inventory details
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("inventory")
        request {
            pathParameter<String>("listingId") {
                description = "Listing ID"
                example("Inventory") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Inventory> {
                    example("Inventory for listing") {
                        value =
                            Inventory(
                                id = 1,
                                listingId = 1,
                                totalQuantity = 10,
                                reservedQuantity = 1,
                            )
                    }
                }
            }
            code(HttpStatusCode.BadRequest) {
                body<ErrorResponse> {
                    example("Invalid ID") {
                        value = ErrorResponse(BAD_REQUEST, "Invalid listing ID")
                    }
                    example("Negative total quantity") {
                        value = ErrorResponse(VALIDATION_ERROR, "Total quantity cannot be negative")
                    }
                    example("Negative reserved quantity") {
                        value = ErrorResponse(VALIDATION_ERROR, "Reserved quantity cannot be negative")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update inventory")
            notFoundExample("Inventory not found")
            conflictExample(
                "Reserved exceeds total" to "Reserved quantity cannot exceed total quantity",
                "Total below reserved" to "Cannot set total quantity below current reserved quantity",
            )
        }
    }

private fun updateInventoryDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Update Inventory"
        description =
            """
            Update inventory quantities for a listing with comprehensive validation rules.

            **Updatable Fields:**
            - **totalQuantity**: Update total stock in warehouse
            - **reservedQuantity**: Update reserved stock for pending orders

            **Partial Update Support:**
            All fields are optional. Only provided fields will be updated.

            **Validation Rules:**
            - Total quantity cannot be negative
            - Reserved quantity cannot be negative
            - Reserved quantity cannot exceed total quantity (enforced with 409 Conflict)
            - Cannot reduce total quantity below current reserved quantity

            **Stock Management:**
            - Increase totalQuantity when receiving new stock
            - Adjust reservedQuantity when processing orders
            - Available quantity is automatically calculated

            **Use Cases:**
            - Receive new stock shipments
            - Process order reservations
            - Handle order cancellations
            - Correct inventory discrepancies

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update inventory
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("inventory")
        request {
            pathParameter<String>("listingId") {
                description = "Listing ID"
                example("Inventory") {
                    value = "1"
                }
            }
            body<UpdateInventoryRequest> {
                description = "All fields are optional. Only provided fields will be updated."
                example("Update inventory") {
                    value =
                        UpdateInventoryRequest(
                            totalQuantity = 100,
                            reservedQuantity = 5,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Inventory> {
                    example("Inventory for listing") {
                        value =
                            Inventory(
                                id = 1,
                                listingId = 1,
                                totalQuantity = 100,
                                reservedQuantity = 5,
                            )
                    }
                }
            }
            badRequestExample("Invalid listing ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can view inventory")
            notFoundExample("Inventory not found")
        }
    }
