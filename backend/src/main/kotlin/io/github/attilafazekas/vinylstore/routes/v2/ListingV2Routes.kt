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

import io.github.attilafazekas.vinylstore.BAD_REQUEST
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V2
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.InventoryV2
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.ListingV2Response
import io.github.attilafazekas.vinylstore.models.ListingsV2Response
import io.github.attilafazekas.vinylstore.models.VinylWithDetailsV2
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toDoubleOrNull
import kotlin.text.toIntOrNull
import kotlin.text.uppercase

fun Route.listingV2Routes(store: VinylStoreData) {
    route("$V2/listings") {
        get(listListingsV2Documentation()) {
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
                    val artists = store.getArtistsForVinyl(vinyl.id)
                    if (artists.isEmpty()) return@mapNotNull null
                    val label = store.labels[vinyl.labelId] ?: return@mapNotNull null
                    val genre = store.getGenreForVinyl(vinyl.id) ?: return@mapNotNull null
                    val inventory = store.getInventoryByListingId(listing.id) ?: return@mapNotNull null

                    if (inventory.availableQuantity > 0) {
                        ListingV2Response(
                            id = listing.id,
                            status = listing.status,
                            price = listing.price,
                            currency = listing.currency,
                            createdAt = listing.createdAt,
                            updatedAt = listing.updatedAt,
                            inventory =
                                InventoryV2(
                                    totalQuantity = inventory.totalQuantity,
                                    reservedQuantity = inventory.reservedQuantity,
                                    availableQuantity = inventory.availableQuantity,
                                    createdAt = TimestampUtil.now(),
                                    updatedAt = TimestampUtil.now(),
                                ),
                            vinyl =
                                VinylWithDetailsV2(
                                    id = vinyl.id,
                                    title = vinyl.title,
                                    year = vinyl.year,
                                    conditionMedia = vinyl.conditionMedia,
                                    conditionSleeve = vinyl.conditionSleeve,
                                    artists = artists,
                                    label = label,
                                    genre = genre,
                                    createdAt = TimestampUtil.now(),
                                    updatedAt = TimestampUtil.now(),
                                ),
                        )
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
                    details = details.filter { it.status == statusEnum }
                }
            }

            minPriceParam?.let { minPrice ->
                details = details.filter { it.price >= minPrice }
            }
            maxPriceParam?.let { maxPrice ->
                details = details.filter { it.price <= maxPrice }
            }

            artistParam?.let { artist ->
                val artistId = artist.toIntOrNull()
                details =
                    details.filter {
                        if (artistId != null) {
                            it.vinyl.artists.any { a -> a.id == artistId }
                        } else {
                            it.vinyl.artists.any { a -> a.name.contains(artist, ignoreCase = true) }
                        }
                    }
            }

            genreParam?.let { genreFilter ->
                details =
                    details.filter { detail ->
                        detail.vinyl.genre.name
                            .contains(genreFilter, ignoreCase = true)
                    }
            }

            labelParam?.let { label ->
                val labelId = label.toIntOrNull()
                details =
                    details.filter {
                        if (labelId != null) {
                            it.vinyl.label.id == labelId
                        } else {
                            it.vinyl.label.name
                                .contains(label, ignoreCase = true)
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

            call.respond(ListingsV2Response(listings = details, total = details.size))
        }

        get("/{id}", getListingV2Documentation()) {
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

            val label = store.labels[vinyl.labelId]
            if (label == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Label not found"))
                return@get
            }

            val genre = store.getGenreForVinyl(vinyl.id)
            if (genre == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Genre not found"))
                return@get
            }

            val inv = store.getInventoryByListingId(listing.id)
            if (inv == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Inventory not found"))
                return@get
            }

            val response =
                ListingV2Response(
                    id = listing.id,
                    status = listing.status,
                    price = listing.price,
                    currency = listing.currency,
                    createdAt = listing.createdAt,
                    updatedAt = listing.updatedAt,
                    inventory =
                        InventoryV2(
                            totalQuantity = inv.totalQuantity,
                            reservedQuantity = inv.reservedQuantity,
                            availableQuantity = inv.availableQuantity,
                            createdAt = TimestampUtil.now(),
                            updatedAt = TimestampUtil.now(),
                        ),
                    vinyl =
                        VinylWithDetailsV2(
                            id = vinyl.id,
                            title = vinyl.title,
                            year = vinyl.year,
                            conditionMedia = vinyl.conditionMedia,
                            conditionSleeve = vinyl.conditionSleeve,
                            artists = artists,
                            label = label,
                            genre = genre,
                            createdAt = TimestampUtil.now(),
                            updatedAt = TimestampUtil.now(),
                        ),
                )

            call.respond(response)
        }
    }
}

private fun listListingsV2Documentation(): RouteConfig.() -> Unit =
    {
        operationId = "listListingsV2"
        summary = "List Listings (V2)"
        description =
            """
            Retrieve all published listings with fully embedded hierarchical data structure.

            **V2 Enhancements:**
            This endpoint provides a hierarchical response structure that embeds all related data,
            eliminating the need for multiple API calls to fetch artist, label, genre, and inventory details.

            **Filtering Options:**
            - **status**: Filter by listing status (DRAFT, PUBLISHED, ARCHIVED)
            - **minPrice/maxPrice**: Filter by price range
            - **artist**: Filter by artist name (partial match) or exact artist ID
            - **genre**: Filter by genre name (partial match)
            - **label**: Filter by label name (partial match) or exact label ID
            - **year**: Filter by exact release year
            - **condition**: Search in media or sleeve condition ratings

            **Embedded Data:**
            - Vinyl details with nested artist, label, and genres
            - Inventory quantities (total, reserved, available)
            - All data in single response without additional requests

            **Access Requirements:**
            - No authentication required
            - Public endpoint accessible to all users
            """.trimIndent()
        tags = listOf("listings-v2")
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
                body<ListingsV2Response> {
                    example("Available listings with embedded data") {
                        value =
                            ListingsV2Response(
                                listings =
                                    listOf(
                                        ListingV2Response(
                                            id = 1,
                                            status = ListingStatus.PUBLISHED,
                                            price = 99.99,
                                            currency = "EUR",
                                            createdAt = "2025-01-10T14:30:45.123Z",
                                            updatedAt = "2025-01-10T14:30:45.123Z",
                                            inventory =
                                                InventoryV2(
                                                    totalQuantity = 15,
                                                    reservedQuantity = 0,
                                                    availableQuantity = 15,
                                                    createdAt = TimestampUtil.now(),
                                                    updatedAt = TimestampUtil.now(),
                                                ),
                                            vinyl =
                                                VinylWithDetailsV2(
                                                    id = 1,
                                                    title = "Avichrom",
                                                    year = 2022,
                                                    conditionMedia = "M",
                                                    conditionSleeve = "M",
                                                    artists = listOf(Artist(1, "Dominik Eulberg")),
                                                    label = Label(1, "!K7 Records"),
                                                    genre = Genre(1, "Electronic"),
                                                    createdAt = TimestampUtil.now(),
                                                    updatedAt = TimestampUtil.now(),
                                                ),
                                        ),
                                        ListingV2Response(
                                            id = 2,
                                            status = ListingStatus.PUBLISHED,
                                            price = 149.99,
                                            currency = "EUR",
                                            createdAt = "2025-01-10T14:30:45.123Z",
                                            updatedAt = "2025-01-10T14:30:45.123Z",
                                            inventory =
                                                InventoryV2(
                                                    totalQuantity = 5,
                                                    reservedQuantity = 2,
                                                    availableQuantity = 3,
                                                    createdAt = TimestampUtil.now(),
                                                    updatedAt = TimestampUtil.now(),
                                                ),
                                            vinyl =
                                                VinylWithDetailsV2(
                                                    id = 3,
                                                    title = "...A Little Further",
                                                    year = 2014,
                                                    conditionMedia = "M",
                                                    conditionSleeve = "NM",
                                                    artists =
                                                        listOf(
                                                            Artist(1, "Dominik Eulberg"),
                                                            Artist(2, "Extrawelt"),
                                                        ),
                                                    label = Label(2, "Cocoon Recordings"),
                                                    genre = Genre(1, "Electronic"),
                                                    createdAt = TimestampUtil.now(),
                                                    updatedAt = TimestampUtil.now(),
                                                ),
                                        ),
                                    ),
                                total = 2,
                            )
                    }
                    example("Empty results") {
                        value =
                            ListingsV2Response(
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

private fun getListingV2Documentation(): RouteConfig.() -> Unit =
    {
        operationId = "getListingV2"
        summary = "Get Listing (V2)"
        description =
            """
            Retrieve a single listing with all related data embedded in hierarchical structure.

            **V2 Enhancements:**
            Unlike v1 which returns separate entities with ID references, v2 embeds complete nested objects:
            - Vinyl details contain embedded artist, label, and genres objects
            - Inventory data is included directly in the listing response
            - Single API call replaces multiple v1 requests

            **Response Structure:**
            - Listing metadata (ID, status, price, currency, timestamps)
            - Nested vinyl object with embedded artist, label, and genres
            - Inventory quantities object (total, reserved, available)

            **Use Cases:**
            - Optimized for mobile apps with limited network requests
            - Reduced latency for detail page rendering
            - Complete data in single response for offline caching

            **Access Requirements:**
            - No authentication required
            - Public endpoint accessible to all users
            """.trimIndent()
        tags = listOf("listings-v2")
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
                body<ListingV2Response> {
                    example("Listing with full details") {
                        value =
                            ListingV2Response(
                                id = 1,
                                status = ListingStatus.PUBLISHED,
                                price = 99.99,
                                currency = "EUR",
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T14:30:45.123Z",
                                inventory =
                                    InventoryV2(
                                        totalQuantity = 15,
                                        reservedQuantity = 0,
                                        availableQuantity = 15,
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                                vinyl =
                                    VinylWithDetailsV2(
                                        id = 1,
                                        title = "Avichrom",
                                        year = 2022,
                                        conditionMedia = "M",
                                        conditionSleeve = "M",
                                        artists = listOf(Artist(1, "Dominik Eulberg")),
                                        label = Label(1, "!K7 Records"),
                                        genre = Genre(1, "Electronic"),
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                            )
                    }
                    example("Listing with collaboration and low inventory") {
                        value =
                            ListingV2Response(
                                id = 2,
                                status = ListingStatus.PUBLISHED,
                                price = 149.99,
                                currency = "EUR",
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T14:30:45.123Z",
                                inventory =
                                    InventoryV2(
                                        totalQuantity = 5,
                                        reservedQuantity = 2,
                                        availableQuantity = 3,
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                                vinyl =
                                    VinylWithDetailsV2(
                                        id = 3,
                                        title = "...A Little Further",
                                        year = 2014,
                                        conditionMedia = "M",
                                        conditionSleeve = "NM",
                                        artists =
                                            listOf(
                                                Artist(1, "Dominik Eulberg"),
                                                Artist(2, "Extrawelt"),
                                            ),
                                        label = Label(2, "Cocoon Recordings"),
                                        genre = Genre(1, "Electronic"),
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
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
