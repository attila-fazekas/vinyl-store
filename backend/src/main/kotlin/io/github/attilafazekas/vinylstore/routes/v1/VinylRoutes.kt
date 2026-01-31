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
import io.github.attilafazekas.vinylstore.TimestampUtil
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
import io.github.attilafazekas.vinylstore.models.CreateVinylRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.Listing
import io.github.attilafazekas.vinylstore.models.UpdateVinylRequest
import io.github.attilafazekas.vinylstore.models.Vinyl
import io.github.attilafazekas.vinylstore.models.VinylDetailResponse
import io.github.attilafazekas.vinylstore.models.VinylsResponse
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

fun Route.vinylRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/vinyls") {
            get(listVinylsDocumentation()) {
                val artistParam = call.parameters["artist"]
                val genreParam = call.parameters["genre"]
                val labelParam = call.parameters["label"]
                val yearParam = call.parameters["year"]?.toIntOrNull()
                val minYearParam = call.parameters["minYear"]?.toIntOrNull()
                val maxYearParam = call.parameters["maxYear"]?.toIntOrNull()
                val titleParam = call.parameters["title"]

                var vinyls = store.vinyls.values.sortedBy { it.id }

                artistParam?.let { artist ->
                    val artistId = artist.toIntOrNull()
                    vinyls =
                        vinyls.filter { vinyl ->
                            if (artistId != null) {
                                vinyl.artistId == artistId
                            } else {
                                val vinylArtist = store.artists[vinyl.artistId]
                                vinylArtist?.name?.contains(artist, ignoreCase = true) == true
                            }
                        }
                }

                genreParam?.let { genreFilter ->
                    vinyls =
                        vinyls.filter { vinyl ->
                            val genre = store.getGenreForVinyl(vinyl.id)
                            genre?.name?.contains(genreFilter, ignoreCase = true) == true
                        }
                }

                labelParam?.let { label ->
                    val labelId = label.toIntOrNull()
                    vinyls =
                        vinyls.filter { vinyl ->
                            if (labelId != null) {
                                vinyl.labelId == labelId
                            } else {
                                val vinylLabel = store.labels[vinyl.labelId]
                                vinylLabel?.name?.contains(label, ignoreCase = true) == true
                            }
                        }
                }

                // Apply year filters (exact year takes precedence)
                yearParam?.let { year ->
                    vinyls = vinyls.filter { it.year == year }
                } ?: run {
                    // Apply year range filters if exact year not specified
                    minYearParam?.let { minYear ->
                        vinyls = vinyls.filter { it.year >= minYear }
                    }
                    maxYearParam?.let { maxYear ->
                        vinyls = vinyls.filter { it.year <= maxYear }
                    }
                }

                titleParam?.let { title ->
                    vinyls = vinyls.filter { it.title.contains(title, ignoreCase = true) }
                }

                call.respond(VinylsResponse(vinyls, vinyls.size))
            }

            get("/{id}", getVinylDocumentation()) {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid vinyl ID"))
                    return@get
                }

                val vinyl = store.vinyls[id]
                if (vinyl == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Vinyl not found"))
                    return@get
                }

                val artist = store.artists[vinyl.artistId]!!
                val label = store.labels[vinyl.labelId]!!
                val genre = store.getGenreForVinyl(vinyl.id)!!

                call.respond(VinylDetailResponse(vinyl, artist, label, genre))
            }

            post(createVinylDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)
                val request = call.receive<CreateVinylRequest>()

                val vinyl =
                    store.createVinyl(
                        request.title,
                        request.artistId,
                        request.labelId,
                        request.genreId,
                        request.year,
                        request.conditionMedia,
                        request.conditionSleeve,
                    )

                store.linkVinylGenre(vinyl.id, request.genreId)

                call.respond(HttpStatusCode.Created, vinyl)
            }

            put("/{id}", updateVinylDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid vinyl ID"))
                    return@put
                }

                val request = call.receive<UpdateVinylRequest>()
                val updated =
                    store.updateVinyl(
                        id,
                        request.title,
                        request.artistId,
                        request.labelId,
                        request.year,
                        request.conditionMedia,
                        request.conditionSleeve,
                    )

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Vinyl not found"))
                    return@put
                }

                // Update genres if provided
                request.genreIds?.let { genreIds ->
                    store.unlinkAllVinylGenres(id)
                    genreIds.forEach { genreId ->
                        store.linkVinylGenre(id, genreId)
                    }
                }

                call.respond(updated)
            }

            delete("/{id}", deleteVinylDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid vinyl ID"))
                    return@delete
                }

                if (store.vinyls[id] == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Vinyl not found"))
                    return@delete
                }

                val hasListings = store.listings.values.any { it.vinylId == id }
                if (hasListings) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Cannot delete vinyl with associated listings"),
                    )
                    return@delete
                }

                store.deleteVinyl(id)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/{vinylId}/listings", createListingDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val vinylId = call.parameters["vinylId"]?.toIntOrNull()
                if (vinylId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid vinyl ID"))
                    return@post
                }

                if (store.vinyls[vinylId] == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Vinyl not found"))
                    return@post
                }

                val request = call.receive<CreateListingRequest>()

                if (request.price <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Price must be positive"))
                    return@post
                }

                if (request.initialStock <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(BAD_REQUEST, "Initial stock must be positive"),
                    )
                    return@post
                }

                val listing = store.createListing(vinylId, request.price, request.currency, request.initialStock)
                call.respond(HttpStatusCode.Created, listing)
            }
        }
    }
}

private fun listVinylsDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "List Vinyls"
        description =
            """
            Retrieve all vinyl records in the catalog with comprehensive filtering capabilities.

            **Filtering Options:**
            - **artist**: Filter by artist name (partial match) or exact artist ID
            - **genre**: Filter by genre name (partial match)
            - **label**: Filter by label name (partial match) or exact label ID
            - **year**: Filter by exact release year
            - **minYear/maxYear**: Filter by release year range
            - **title**: Search in title (case-insensitive partial match)

            **Search Features:**
            - Multiple filters can be combined
            - Partial matching for flexible search
            - Case-insensitive search
            - Year range or exact year filtering

            **Response Data:**
            Returns vinyl records with ID references to artist and label.
            Use additional requests to fetch artist/label/genre details, or use v2 endpoint for embedded data.

            **Use Cases:**
            - Browse vinyl catalog
            - Search for specific records
            - Filter by artist, genre, or label
            - Find records by release year

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("vinyls")
        request {
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
                description = "Filter by exact release year"
                required = false
            }
            queryParameter<Int>("minYear") {
                description = "Filter by minimum release year"
                required = false
            }
            queryParameter<Int>("maxYear") {
                description = "Filter by maximum release year"
                required = false
            }
            queryParameter<String>("title") {
                description = "Search in title (case-insensitive partial match)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<VinylsResponse> {
                    example("Vinyls list") {
                        value =
                            VinylsResponse(
                                vinyls =
                                    listOf(
                                        Vinyl(
                                            id = 1,
                                            title = "Avichrom",
                                            artistId = 1,
                                            labelId = 1,
                                            genreId = 1,
                                            year = 2022,
                                            conditionMedia = "M",
                                            conditionSleeve = "M",
                                            createdAt = TimestampUtil.now(),
                                            updatedAt = TimestampUtil.now(),
                                        ),
                                        Vinyl(
                                            id = 2,
                                            title = "...A Little Further",
                                            artistId = 1,
                                            labelId = 2,
                                            genreId = 1,
                                            year = 2014,
                                            conditionMedia = "M",
                                            conditionSleeve = "NM",
                                            createdAt = TimestampUtil.now(),
                                            updatedAt = TimestampUtil.now(),
                                        ),
                                    ),
                                total = 2,
                            )
                    }
                }
            }
            badRequestExample("Invalid filter value")
            notAuthenticatedExample()
        }
    }

private fun getVinylDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Vinyl"
        description =
            """
            Retrieve detailed information about a specific vinyl record with embedded details.

            **Returns:**
            - Complete vinyl record information
            - Embedded artist object
            - Embedded label object
            - Array of associated genre objects

            **Embedded Details:**
            Unlike the list endpoint, this returns full artist, label, and genre objects
            rather than just IDs, reducing the need for additional API calls.

            **Use Cases:**
            - Display vinyl detail pages
            - Show complete product information
            - Verify vinyl data before creating listings

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("vinyls")
        request {
            pathParameter<String>("id") {
                description = "Vinyl ID"
                example("Vinyl details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<VinylDetailResponse> {
                    example("Vinyl with details") {
                        value =
                            VinylDetailResponse(
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
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                                artist = Artist(1, "Dominik Eulberg"),
                                label = Label(1, "!K7 Records"),
                                genre = Genre(1, "Electronic"),
                            )
                    }
                }
            }
            badRequestExample("Invalid vinyl ID")
            notAuthenticatedExample()
            notFoundExample("Vinyl not found")
        }
    }

private fun createVinylDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Create Vinyl"
        description =
            """
            Create a new vinyl record in the catalog with associated artist, label, and genres.

            **Required Fields:**
            - **title**: Vinyl record title
            - **artistId**: Must reference existing artist
            - **labelId**: Must reference existing label
            - **year**: Release year
            - **conditionMedia**: Media condition rating (e.g., M, NM, VG+, VG, G)
            - **conditionSleeve**: Sleeve condition rating
            - **genreIds**: Array of genre IDs (can be empty)

            **Genre Associations:**
            - Multiple genres can be associated
            - Genres are linked via separate vinyl-genre relationships
            - Genre IDs must reference existing genres

            **Condition Ratings:**
            Common ratings: M (Mint), NM (Near Mint), VG+ (Very Good Plus), VG (Very Good), G (Good)

            **Use Cases:**
            - Add new vinyl records to catalog
            - Expand inventory with new releases
            - Create records before creating listings

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can create vinyls
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("vinyls")
        request {
            body<CreateVinylRequest> {
                description =
                    "Vinyl details including title, artist, label, year, condition ratings, and genre associations."
                example("New vinyl") {
                    value =
                        CreateVinylRequest(
                            title = "The Loud Silence",
                            artistId = 1,
                            labelId = 1,
                            genreId = 1,
                            year = 2015,
                            conditionMedia = "VG",
                            conditionSleeve = "VG+",
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Vinyl> {
                    example("New vinyl") {
                        value =
                            CreateVinylRequest(
                                title = "The Loud Silence",
                                artistId = 1,
                                labelId = 1,
                                genreId = 1,
                                year = 2015,
                                conditionMedia = "VG",
                                conditionSleeve = "VG+",
                            )
                    }
                }
            }
            validationErrorExample("Invalid vinyl data")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can create vinyls")
        }
    }

private fun updateVinylDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Update Vinyl"
        description =
            """
            Update vinyl record details including genre associations with support for partial updates.

            **Updatable Fields:**
            - **title**: Vinyl record title
            - **artistId**: Associated artist (must exist)
            - **labelId**: Associated label (must exist)
            - **year**: Release year
            - **conditionMedia**: Media condition rating
            - **conditionSleeve**: Sleeve condition rating
            - **genreIds**: Array of genre IDs (replaces all existing associations)

            **Partial Update Support:**
            All fields are optional. Only provided fields will be updated.

            **Genre Association Updates:**
            - Providing genreIds replaces ALL existing genre associations
            - Omitting genreIds leaves genre associations unchanged
            - Empty array removes all genre associations

            **Use Cases:**
            - Correct vinyl information
            - Update condition ratings over time
            - Change artist or label associations
            - Adjust genre classifications

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update vinyls
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("vinyls")
        request {
            pathParameter<String>("id") {
                description = "Vinyl ID"
                example("Update vinyl") {
                    value = "1"
                }
            }
            body<UpdateVinylRequest> {
                description = "All fields are optional. Only provided fields will be updated."
                example("Update condition") {
                    value =
                        UpdateVinylRequest(
                            conditionMedia = "VG",
                            conditionSleeve = "VG",
                        )
                }
                example("Update year") {
                    value = UpdateVinylRequest(year = 2015)
                }
                example("Update genres") {
                    value = UpdateVinylRequest(genreIds = listOf(1, 7, 9))
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Vinyl> {
                    example("Updated vinyl") {
                        value =
                            Vinyl(
                                id = 1,
                                title = "Avichrom",
                                artistId = 1,
                                labelId = 1,
                                genreId = 1,
                                year = 2022,
                                conditionMedia = "VG",
                                conditionSleeve = "VG",
                                createdAt = TimestampUtil.now(),
                                updatedAt = TimestampUtil.now(),
                            )
                    }
                }
            }
            badRequestExample("Invalid vinyl ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update vinyls")
            notFoundExample("Vinyl not found")
        }
    }

private fun deleteVinylDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Delete Vinyl"
        description =
            """
            Permanently delete a vinyl record from the catalog.

            **Referential Integrity Constraints:**
            - Cannot delete vinyls with associated listings
            - Returns 409 Conflict if vinyl has listings
            - Protects against orphaned listing records
            - Automatically removes vinyl-genre associations

            **Best Practices:**
            - Delete or archive all listings before deleting vinyl
            - Verify no listings reference this vinyl
            - Consider keeping vinyls for historical records

            **Use Cases:**
            - Remove vinyls created in error
            - Clean up duplicate vinyl entries
            - Maintain catalog quality

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can delete vinyls
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("vinyls")
        request {
            pathParameter<String>("id") {
                description = "Vinyl ID"
                example("Delete vinyl") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Vinyl deleted successfully"
            }
            badRequestExample("Invalid vinyl ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can delete vinyls")
            notFoundExample("Vinyl not found")
            code(HttpStatusCode.Conflict) {
                body<ErrorResponse> {
                    example("Vinyl has listings") {
                        value =
                            ErrorResponse(CONFLICT, "Cannot delete vinyl with associated listings")
                    }
                }
            }
        }
    }

private fun createListingDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Create Listing for Vinyl"
        description =
            """
            Create a new listing for a specific vinyl record with initial inventory.

            **Required Fields:**
            - **price**: Listing price (must be positive)
            - **currency**: Currency code (defaults to 'EUR')
            - **initialStock**: Initial inventory quantity (must be positive)

            **Automatic Operations:**
            - Creates listing in PUBLISHED status
            - Automatically creates associated inventory record
            - Sets timestamps (createdAt, updatedAt)

            **Validation:**
            - Vinyl must exist (returns 404 if not found)
            - Price must be positive
            - Initial stock must be positive

            **Use Cases:**
            - List existing vinyl records for sale
            - Add new inventory to catalog
            - Create listings with initial stock

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can create listings
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("vinyls", "listings")
        request {
            pathParameter<String>("vinylId") {
                description = "ID of the vinyl record to create a listing for"
                example("Create listing") {
                    value = "1"
                }
            }
            body<CreateListingRequest> {
                description = "Listing details including price, currency, and initial stock quantity."
                example("New listing") {
                    value =
                        CreateListingRequest(
                            price = 29.99,
                            currency = "EUR",
                            initialStock = 10,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Listing> {
                    example("Created listing") {
                        value =
                            Listing(
                                id = 1,
                                vinylId = 1,
                                status = ListingStatus.PUBLISHED,
                                price = 29.99,
                                currency = "EUR",
                                createdAt = "2025-01-10T14:30:45.123Z",
                                updatedAt = "2025-01-10T14:30:45.123Z",
                            )
                    }
                }
            }
            code(HttpStatusCode.BadRequest) {
                body<ErrorResponse> {
                    example("Invalid vinyl ID") {
                        value = ErrorResponse(BAD_REQUEST, "Invalid vinyl ID")
                    }
                    example("Price must be positive") {
                        value = ErrorResponse(VALIDATION_ERROR, "Price must be positive")
                    }
                    example("Initial stock must be positive") {
                        value = ErrorResponse(VALIDATION_ERROR, "Initial stock must be positive")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can create listings")
            notFoundExample("Vinyl not found")
            conflictExample("Listing already exists" to "A listing already exists for this vinyl")
        }
    }
