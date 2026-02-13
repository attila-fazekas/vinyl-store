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

package io.github.attilafazekas.vinylstore.routes.v2

import io.github.attilafazekas.vinylstore.AUTH_JWT
import io.github.attilafazekas.vinylstore.BAD_REQUEST
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.V2
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.insufficientPermissionsExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.InventoriesV2Response
import io.github.attilafazekas.vinylstore.models.InventoryWithListingV2Response
import io.github.attilafazekas.vinylstore.models.ListingContextV2
import io.github.attilafazekas.vinylstore.models.VinylContextV2
import io.github.attilafazekas.vinylstore.requireRole
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toIntOrNull

fun Route.inventoryV2Routes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V2/inventory") {
            get(listInventoryV2Documentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val outOfStockParam = call.parameters["outOfStock"]?.toBoolean() ?: false
                val artistParam = call.parameters["artist"]
                val titleParam = call.parameters["title"]
                val minAvailableParam = call.parameters["minAvailable"]?.toIntOrNull()
                val maxAvailableParam = call.parameters["maxAvailable"]?.toIntOrNull()

                var inventoryList =
                    store.inventory.values
                        .mapNotNull { inv ->
                            val listing = store.getListingById(inv.listingId) ?: return@mapNotNull null
                            val vinyl = store.vinyls[listing.vinylId] ?: return@mapNotNull null
                            val artists = store.getArtistsForVinyl(vinyl.id)
                            if (artists.isEmpty()) return@mapNotNull null

                            InventoryWithListingV2Response(
                                id = inv.id,
                                totalQuantity = inv.totalQuantity,
                                reservedQuantity = inv.reservedQuantity,
                                availableQuantity = inv.availableQuantity,
                                createdAt = inv.createdAt,
                                updatedAt = inv.updatedAt,
                                listing =
                                    ListingContextV2(
                                        id = listing.id,
                                        status = listing.status,
                                        price = listing.price,
                                        currency = listing.currency,
                                        vinyl =
                                            VinylContextV2(
                                                id = vinyl.id,
                                                title = vinyl.title,
                                                artists = artists,
                                            ),
                                    ),
                            )
                        }.sortedBy { it.id }

                if (outOfStockParam) {
                    inventoryList = inventoryList.filter { it.availableQuantity == 0 }
                }

                minAvailableParam?.let { min ->
                    inventoryList = inventoryList.filter { it.availableQuantity >= min }
                }
                maxAvailableParam?.let { max ->
                    inventoryList = inventoryList.filter { it.availableQuantity <= max }
                }

                artistParam?.let { artist ->
                    val artistId = artist.toIntOrNull()
                    inventoryList =
                        inventoryList.filter { inv ->
                            if (artistId != null) {
                                inv.listing.vinyl.artists
                                    .any { it.id == artistId }
                            } else {
                                inv.listing.vinyl.artists
                                    .any { it.name.contains(artist, ignoreCase = true) }
                            }
                        }
                }

                titleParam?.let { title ->
                    inventoryList =
                        inventoryList.filter {
                            it.listing.vinyl.title
                                .contains(title, ignoreCase = true)
                        }
                }

                call.respond(InventoriesV2Response(inventory = inventoryList, total = inventoryList.size))
            }

            get("/{listingId}", getInventoryWithContextDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val listingId = call.parameters["listingId"]?.toIntOrNull()
                if (listingId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@get
                }

                val inv = store.getInventoryByListingId(listingId)
                if (inv == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Inventory not found"))
                    return@get
                }

                val listing = store.getListingById(listingId)
                if (listing == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                    return@get
                }

                val vinyl = store.vinyls[listing.vinylId]
                if (vinyl == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Vinyl not found"))
                    return@get
                }

                val artists = store.getArtistsForVinyl(vinyl.id)
                if (artists.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Artists not found"))
                    return@get
                }

                val response =
                    InventoryWithListingV2Response(
                        id = inv.id,
                        totalQuantity = inv.totalQuantity,
                        reservedQuantity = inv.reservedQuantity,
                        availableQuantity = inv.availableQuantity,
                        createdAt = inv.createdAt,
                        updatedAt = inv.updatedAt,
                        listing =
                            ListingContextV2(
                                id = listing.id,
                                status = listing.status,
                                price = listing.price,
                                currency = listing.currency,
                                vinyl =
                                    VinylContextV2(
                                        id = vinyl.id,
                                        title = vinyl.title,
                                        artists = artists,
                                    ),
                            ),
                    )

                call.respond(response)
            }
        }
    }
}

private fun listInventoryV2Documentation(): RouteConfig.() -> Unit =
    {
        summary = "List Inventory (V2)"
        description =
            """
            Retrieve all inventory records with embedded listing and vinyl context.

            **V2 Enhancements:**
            This endpoint provides a complete overview of inventory with product identification:
            - All inventory records with quantities
            - Embedded listing context (status, price)
            - Vinyl details with artist names for easy identification
            - Powerful filtering for inventory management

            **Filtering Options:**
            - **outOfStock**: Show only items with availableQuantity = 0 (boolean)
            - **minAvailable/maxAvailable**: Filter by available quantity range
            - **artist**: Filter by artist name (partial match) or exact artist ID
            - **title**: Search vinyl titles (case-insensitive partial match)

            **Use Cases:**
            - Monitor stock levels across all products
            - Identify low stock items for restocking
            - Generate inventory reports
            - Search inventory by product or artist
            - Export data for external systems

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can view inventory list
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("inventory-v2")
        request {
            queryParameter<Boolean>("outOfStock") {
                description = "Show only out of stock items (availableQuantity = 0)"
                required = false
            }
            queryParameter<Int>("minAvailable") {
                description = "Minimum available quantity filter"
                required = false
            }
            queryParameter<Int>("maxAvailable") {
                description = "Maximum available quantity filter"
                required = false
            }
            queryParameter<String>("artist") {
                description = "Filter by artist name (partial match) or exact artist ID"
                required = false
            }
            queryParameter<String>("title") {
                description = "Search in vinyl titles (case-insensitive partial match)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<InventoriesV2Response> {
                    example("All inventory records") {
                        value =
                            InventoriesV2Response(
                                inventory =
                                    listOf(
                                        InventoryWithListingV2Response(
                                            id = 1,
                                            totalQuantity = 15,
                                            reservedQuantity = 2,
                                            availableQuantity = 13,
                                            createdAt = "2025-01-10T14:30:45.123Z",
                                            updatedAt = "2025-01-10T14:30:45.123Z",
                                            listing =
                                                ListingContextV2(
                                                    id = 1,
                                                    status = ListingStatus.PUBLISHED,
                                                    price = 99.99,
                                                    currency = "EUR",
                                                    vinyl =
                                                        VinylContextV2(
                                                            id = 1,
                                                            title = "Avichrom",
                                                            artists =
                                                                listOf(
                                                                    Artist(id = 1, name = "Dominik Eulberg"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                        InventoryWithListingV2Response(
                                            id = 2,
                                            totalQuantity = 3,
                                            reservedQuantity = 0,
                                            availableQuantity = 3,
                                            createdAt = "2025-01-10T14:30:45.123Z",
                                            updatedAt = "2025-01-10T14:30:45.123Z",
                                            listing =
                                                ListingContextV2(
                                                    id = 3,
                                                    status = ListingStatus.PUBLISHED,
                                                    price = 65.05,
                                                    currency = "EUR",
                                                    vinyl =
                                                        VinylContextV2(
                                                            id = 3,
                                                            title = "...A Little Further",
                                                            artists =
                                                                listOf(
                                                                    Artist(id = 1, name = "Dominik Eulberg"),
                                                                    Artist(id = 2, name = "Extrawelt"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                    ),
                                total = 2,
                            )
                    }
                    example("Low stock items") {
                        value =
                            InventoriesV2Response(
                                inventory =
                                    listOf(
                                        InventoryWithListingV2Response(
                                            id = 2,
                                            totalQuantity = 3,
                                            reservedQuantity = 0,
                                            availableQuantity = 3,
                                            createdAt = "2025-01-10T14:30:45.123Z",
                                            updatedAt = "2025-01-10T14:30:45.123Z",
                                            listing =
                                                ListingContextV2(
                                                    id = 3,
                                                    status = ListingStatus.PUBLISHED,
                                                    price = 65.05,
                                                    currency = "EUR",
                                                    vinyl =
                                                        VinylContextV2(
                                                            id = 3,
                                                            title = "...A Little Further",
                                                            artists =
                                                                listOf(
                                                                    Artist(id = 1, name = "Dominik Eulberg"),
                                                                    Artist(id = 2, name = "Extrawelt"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                    ),
                                total = 1,
                            )
                    }
                    example("Empty results") {
                        value =
                            InventoriesV2Response(
                                inventory = emptyList(),
                                total = 0,
                            )
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can view inventory list")
        }
    }

private fun getInventoryWithContextDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Inventory with Context (V2)"
        description =
            """
            Retrieve inventory details with embedded listing, vinyl, and artist context.

            **V2 Enhancements:**
            This endpoint embeds contextual information to help identify which product the inventory belongs to:
            - Inventory quantities (total, reserved, available)
            - Listing context (status, price, currency)
            - Vinyl context (title)
            - Artist information (name)

            **Embedded Data:**
            - Complete inventory record
            - Nested listing object with vinyl and artist details
            - Single API call for full context

            **Use Cases:**
            - Display inventory with product identification
            - Generate stock reports with product names
            - Inventory management dashboards
            - Reduce API calls for inventory displays

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can view inventory details
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("inventory-v2")
        request {
            pathParameter<String>("listingId") {
                description = "Listing ID"
                example("Inventory with listing context") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<InventoryWithListingV2Response> {
                    example("Inventory with listing context") {
                        value =
                            InventoryWithListingV2Response(
                                id = 1,
                                totalQuantity = 10,
                                reservedQuantity = 1,
                                availableQuantity = 9,
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T14:30:45.123Z",
                                listing =
                                    ListingContextV2(
                                        id = 1,
                                        status = ListingStatus.PUBLISHED,
                                        price = 99.99,
                                        currency = "EUR",
                                        vinyl =
                                            VinylContextV2(
                                                id = 1,
                                                title = "Avichrom",
                                                artists =
                                                    listOf(
                                                        Artist(
                                                            id = 1,
                                                            name = "Dominik Eulberg",
                                                        ),
                                                    ),
                                            ),
                                    ),
                            )
                    }
                    example("Inventory with collaboration") {
                        value =
                            InventoryWithListingV2Response(
                                id = 3,
                                totalQuantity = 5,
                                reservedQuantity = 0,
                                availableQuantity = 5,
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T14:30:45.123Z",
                                listing =
                                    ListingContextV2(
                                        id = 3,
                                        status = ListingStatus.PUBLISHED,
                                        price = 65.05,
                                        currency = "EUR",
                                        vinyl =
                                            VinylContextV2(
                                                id = 3,
                                                title = "...A Little Further",
                                                artists =
                                                    listOf(
                                                        Artist(
                                                            id = 1,
                                                            name = "Dominik Eulberg",
                                                        ),
                                                        Artist(
                                                            id = 2,
                                                            name = "Extrawelt",
                                                        ),
                                                    ),
                                            ),
                                    ),
                            )
                    }
                }
            }
            badRequestExample("Invalid listing ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can view inventory details")
            notFoundExample("Inventory not found", "Listing not found")
        }
    }
