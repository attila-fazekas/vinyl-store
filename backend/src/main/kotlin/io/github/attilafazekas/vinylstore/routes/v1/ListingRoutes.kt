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
import io.github.attilafazekas.vinylstore.documentation.validationErrorExample
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.CreateListingRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.Inventory
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.Listing
import io.github.attilafazekas.vinylstore.models.ListingDetailResponse
import io.github.attilafazekas.vinylstore.models.ListingsResponse
import io.github.attilafazekas.vinylstore.models.UpdateListingRequest
import io.github.attilafazekas.vinylstore.models.Vinyl
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
import kotlin.text.toDoubleOrNull
import kotlin.text.toIntOrNull
import kotlin.text.uppercase

fun Route.listingRoutes(store: VinylStoreData) {
    route("$V1/listings") {
        get(listListingsDocumentation()) {
            val statusParam = call.parameters["status"]
            val minPriceParam = call.parameters["minPrice"]?.toDoubleOrNull()
            val maxPriceParam = call.parameters["maxPrice"]?.toDoubleOrNull()
            val artistParam = call.parameters["artist"]
            val genreParam = call.parameters["genre"]
            val labelParam = call.parameters["label"]
            val yearParam = call.parameters["year"]?.toIntOrNull()
            val conditionParam = call.parameters["condition"]

            val allListings = store.getAllPublishedListings()

            var details =
                allListings.mapNotNull { listing ->
                    val vinyl = store.vinyls[listing.vinylId] ?: return@mapNotNull null
                    val artist = store.artists[vinyl.artistId] ?: return@mapNotNull null
                    val label = store.labels[vinyl.labelId] ?: return@mapNotNull null
                    val genre = store.getGenreForVinyl(vinyl.id) ?: return@mapNotNull null
                    val inv = store.getInventoryByListingId(listing.id) ?: return@mapNotNull null

                    if (inv.availableQuantity > 0) {
                        ListingDetailResponse(listing, vinyl, artist, genre, label, inv)
                    } else {
                        null
                    }
                }

            statusParam?.let { status ->
                val statusEnum =
                    try {
                        ListingStatus.valueOf(status.uppercase())
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                statusEnum?.let {
                    details = details.filter { it.listing.status == statusEnum }
                }
            }

            minPriceParam?.let { minPrice ->
                details = details.filter { it.listing.price >= minPrice }
            }
            maxPriceParam?.let { maxPrice ->
                details = details.filter { it.listing.price <= maxPrice }
            }

            artistParam?.let { artist ->
                val artistId = artist.toIntOrNull()
                details =
                    details.filter {
                        if (artistId != null) {
                            it.artist.id == artistId
                        } else {
                            it.artist.name.contains(artist, ignoreCase = true)
                        }
                    }
            }

            genreParam?.let { genreFilter ->
                details =
                    details.filter { detail ->
                        detail.genre.name.contains(genreFilter, ignoreCase = true)
                    }
            }

            labelParam?.let { label ->
                val labelId = label.toIntOrNull()
                details =
                    details.filter {
                        if (labelId != null) {
                            it.label.id == labelId
                        } else {
                            it.label.name.contains(label, ignoreCase = true)
                        }
                    }
            }

            yearParam?.let { year ->
                details = details.filter { it.vinyl.year == year }
            }

            conditionParam?.let { condition ->
                details =
                    details.filter {
                        it.vinyl.conditionMedia.contains(condition, ignoreCase = true) ||
                            it.vinyl.conditionSleeve.contains(condition, ignoreCase = true)
                    }
            }

            call.respond(ListingsResponse(listings = details, total = details.size))
        }

        get("/{id}", getListingDocumentation()) {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                return@get
            }

            val listing = store.getListingById(id)
            if (listing == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                return@get
            }

            val vinyl = store.vinyls[listing.vinylId]!!
            val artist = store.artists[vinyl.artistId]!!
            val label = store.labels[vinyl.labelId]!!
            val genre = store.getGenreForVinyl(vinyl.id)!!
            val inv = store.getInventoryByListingId(listing.id)!!

            call.respond(ListingDetailResponse(listing, vinyl, artist, genre, label, inv))
        }

        authenticate(AUTH_JWT) {
            put("/{id}", updateListingDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@put
                }

                val request = call.receive<UpdateListingRequest>()
                val updated = store.updateListing(id, request.price, request.status)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                } else {
                    call.respond(updated)
                }
            }

            delete("/{id}", deleteListingDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@delete
                }

                if (store.getListingById(id) == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                    return@delete
                }

                // Check if listing has active orders (when orders are implemented)
                // For now, we'll use a placeholder check
                val hasActiveOrders = store.hasActiveOrders(id)
                if (hasActiveOrders) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(
                            CONFLICT,
                            "Cannot delete listing with active orders. Archive it instead.",
                        ),
                    )
                    return@delete
                }

                store.deleteListing(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun listListingsDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "List Listings"
        description =
            """
            Retrieve all published listings with available inventory and complete vinyl details.

            This endpoint provides comprehensive access to the store's catalog with powerful filtering capabilities:

            **Filtering Options:**
            - **status**: Filter by listing status (DRAFT, PUBLISHED, ARCHIVED)
            - **minPrice/maxPrice**: Filter by price range in specified currency
            - **artist**: Filter by artist name (partial match) or exact artist ID
            - **genre**: Filter by genre name (partial match)
            - **label**: Filter by label name (partial match) or exact label ID
            - **year**: Filter by exact release year
            - **condition**: Search in media or sleeve condition ratings

            **Response Data:**
            Each listing includes embedded data for the vinyl record, artist, label, genres, and current inventory levels.
            Only listings with available inventory (totalQuantity - reservedQuantity > 0) are returned.

            **Access Requirements:**
            - No authentication required for viewing published listings
            - Public endpoint accessible to all users
            """.trimIndent()
        tags = listOf("listings")
        request {
            queryParameter<String>("status") {
                description = "Filter by listing status (DRAFT, PUBLISHED, ARCHIVED)"
                required = false
            }
            queryParameter<Double>("minPrice") {
                description = "Minimum price filter"
                required = false
            }
            queryParameter<Double>("maxPrice") {
                description = "Maximum price filter"
                required = false
            }
            queryParameter<String>("artist") {
                description = "Filter by artist name or ID"
                required = false
            }
            queryParameter<String>("genre") {
                description = "Filter by genre name"
                required = false
            }
            queryParameter<String>("label") {
                description = "Filter by label name or ID"
                required = false
            }
            queryParameter<Int>("year") {
                description = "Filter by release year"
                required = false
            }
            queryParameter<String>("condition") {
                description = "Filter by condition (media or sleeve)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<ListingsResponse> {
                    example("Available listings") {
                        value =
                            ListingsResponse(
                                listings =
                                    listOf(
                                        ListingDetailResponse(
                                            listing =
                                                Listing(
                                                    id = 1,
                                                    vinylId = 1,
                                                    status = ListingStatus.PUBLISHED,
                                                    price = 99.99,
                                                    currency = "EUR",
                                                    createdAt = "2025-01-10T14:30:45.123Z",
                                                    updatedAt = "2025-01-10T14:30:45.123Z",
                                                ),
                                            vinyl =
                                                Vinyl(
                                                    id = 1,
                                                    title = "Avichrom",
                                                    artistId = 1,
                                                    labelId = 1,
                                                    genreId = 1,
                                                    year = 2022,
                                                    conditionMedia = "M",
                                                    conditionSleeve = "M",
                                                ),
                                            artist =
                                                Artist(
                                                    id = 1,
                                                    name = "Dominik Eulberg",
                                                ),
                                            genre = Genre(id = 1, name = "Electronic"),
                                            label =
                                                Label(
                                                    id = 1,
                                                    name = "!K7 Records",
                                                ),
                                            inventory =
                                                Inventory(
                                                    id = 1,
                                                    listingId = 1,
                                                    totalQuantity = 15,
                                                    reservedQuantity = 0,
                                                ),
                                        ),
                                    ),
                                total = 1,
                            )
                    }
                    example("Empty results") {
                        value =
                            ListingsResponse(
                                listings = emptyList(),
                                total = 0,
                            )
                    }
                }
            }
            badRequestExample("Invalid filter value")
            notAuthenticatedExample()
        }
    }

private fun getListingDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Listing"
        description =
            """
            Retrieve detailed information about a specific listing by its unique ID.

            **Returns:**
            - Complete listing details (ID, status, price, currency, timestamps)
            - Full vinyl record information (title, year, condition ratings)
            - Associated artist details
            - Associated label details
            - All genre classifications
            - Current inventory levels (total, reserved, and available quantities)

            **Use Cases:**
            - Display detailed product page for customers
            - Check real-time inventory availability
            - Verify listing status and pricing

            **Access Requirements:**
            - No authentication required
            - Public endpoint accessible to all users
            """.trimIndent()
        tags = listOf("listings")
        request {
            pathParameter<String>("id") {
                description = "Listing ID"
                example("Listing details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<ListingDetailResponse> {
                    example("Listing details") {
                        value =
                            ListingDetailResponse(
                                listing =
                                    Listing(
                                        id = 1,
                                        vinylId = 1,
                                        status = ListingStatus.PUBLISHED,
                                        price = 99.99,
                                        currency = "EUR",
                                        createdAt = "2025-01-10T14:30:45.123Z",
                                        updatedAt = "2025-01-10T14:30:45.123Z",
                                    ),
                                vinyl =
                                    Vinyl(
                                        id = 1,
                                        title = "Avichrom",
                                        artistId = 1,
                                        labelId = 1,
                                        genreId = 1,
                                        year = 2022,
                                        conditionMedia = "M",
                                        conditionSleeve = "M",
                                    ),
                                artist =
                                    Artist(
                                        id = 1,
                                        name = "Dominik Eulberg",
                                    ),
                                genre = Genre(id = 1, name = "Electronic"),
                                label =
                                    Label(
                                        id = 1,
                                        name = "!K7 Records",
                                    ),
                                inventory =
                                    Inventory(
                                        id = 1,
                                        listingId = 1,
                                        totalQuantity = 15,
                                        reservedQuantity = 0,
                                    ),
                            )
                    }
                }
            }
            badRequestExample("Invalid listing ID")
            notAuthenticatedExample()
            notFoundExample("Listing not found")
        }
    }

private fun updateListingDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Update Listing"
        description =
            """
            Update an existing listing's price or status with support for partial updates.

            **Updatable Fields:**
            - **price**: Change the listing price (must be positive if provided)
            - **status**: Change listing status (DRAFT, PUBLISHED, ARCHIVED)

            **Partial Update Support:**
            All fields are optional. Only fields explicitly provided in the request will be updated.
            Fields set to null or omitted will retain their current values.

            **Validation Rules:**
            - If price is provided, it must be greater than 0
            - Status must be a valid ListingStatus enum value
            - updatedAt timestamp is automatically refreshed

            **Use Cases:**
            - Adjust pricing for sales or market changes
            - Archive discontinued items (status = ARCHIVED)
            - Draft unpublished listings for later review (status = DRAFT)
            - Publish drafted listings (status = PUBLISHED)

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update listings
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("listings")
        request {
            pathParameter<String>("id") {
                description = "Listing ID"
                example("Update listing") {
                    value = "1"
                }
            }
            body<UpdateListingRequest> {
                description = "All fields are optional. Only provided fields will be updated."
                example("Update price") {
                    value =
                        UpdateListingRequest(
                            price = 34.99,
                        )
                }
                example("Update status") {
                    value =
                        UpdateListingRequest(
                            status = ListingStatus.DRAFT,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Listing> {
                    example("Listing updated") {
                        description = "Example of an updated listing with new price"
                        value =
                            Listing(
                                id = 1,
                                vinylId = 1,
                                status = ListingStatus.PUBLISHED,
                                price = 34.99,
                                currency = "EUR",
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T15:45:30.456Z",
                            )
                    }
                }
            }
            badRequestExample("Invalid listing ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update listings")
            notFoundExample("Listing not found")
        }
    }

private fun deleteListingDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Delete Listing"
        description =
            """
            Permanently delete a listing from the system.

            **Important Constraints:**
            - Cannot delete listings with active or pending orders
            - Listings with order history should be archived instead (set status to ARCHIVED)
            - Deletion is permanent and cannot be undone
            - Associated inventory record will also be removed

            **Validation Rules:**
            - Listing must exist (returns 404 if not found)
            - Listing must not have any active orders (returns 409 Conflict if orders exist)

            **Best Practices:**
            - Consider archiving instead of deleting for historical records
            - Use DELETE only for listings created in error
            - Archive listings (status = ARCHIVED) to preserve order history

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can delete listings
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("listings")
        request {
            pathParameter<String>("id") {
                description = "Listing ID"
                example("Delete listing") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Listing deleted successfully"
            }
            badRequestExample("Invalid listing ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can delete listings")
            notFoundExample("Listing not found")
            conflictExample("Listing has active orders" to "Cannot delete listing with active orders. Archive it instead.")
        }
    }
