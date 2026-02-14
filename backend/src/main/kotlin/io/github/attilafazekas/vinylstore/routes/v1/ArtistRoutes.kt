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
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.conflictExample
import io.github.attilafazekas.vinylstore.documentation.insufficientPermissionsExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.ArtistsResponse
import io.github.attilafazekas.vinylstore.models.CreateArtistRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.UpdateArtistRequest
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
import kotlin.text.toIntOrNull

fun Route.artistRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/artists") {
            get(listArtistsDocumentation()) {
                val nameParam = call.parameters["name"]

                var artists = store.artists.values.sortedBy { it.id }

                nameParam?.let { name ->
                    artists = artists.filter { it.name.contains(name, ignoreCase = true) }
                }

                call.respond(ArtistsResponse(artists, artists.size))
            }

            post(createArtistDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)
                val request = call.receive<CreateArtistRequest>()
                val artist = store.createArtist(request.name)
                call.respond(HttpStatusCode.Created, artist)
            }

            get("/{id}", getArtistDocumentation()) {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid artist ID"))
                    return@get
                }

                val artist = store.artists[id]
                if (artist == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Artist not found"))
                } else {
                    call.respond(artist)
                }
            }

            put("/{id}", updateArtistDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid artist ID"))
                    return@put
                }

                val request = call.receive<UpdateArtistRequest>()
                val updated = store.updateArtist(id, request.name)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Artist not found"))
                } else {
                    call.respond(updated)
                }
            }

            delete("/{id}", deleteArtistDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid artist ID"))
                    return@delete
                }

                if (store.artists[id] == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Artist not found"))
                    return@delete
                }

                val hasVinyls = store.vinylArtists.values.any { it.artistId == id }
                if (hasVinyls) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Cannot delete artist with associated vinyls"),
                    )
                    return@delete
                }

                store.deleteArtist(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun getArtistDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getArtist"
        summary = "Get Artist"
        description =
            """
            Retrieve details of a specific artist by their unique ID.

            **Returns:**
            - Artist ID
            - Artist name

            **Use Cases:**
            - Display artist information
            - Verify artist existence before associations
            - Artist detail pages

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("artists")
        request {
            pathParameter<String>("id") {
                description = "Artist ID"
                example("Artist details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Artist> {
                    example("Artist details") {
                        value = Artist(1, "Dominik Eulberg")
                    }
                }
            }
            badRequestExample("Invalid artist ID")
            notAuthenticatedExample()
            notFoundExample("Artist not found")
        }
    }

private fun listArtistsDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "listArtists"
        summary = "List Artists"
        description =
            """
            Retrieve all artists in the catalog with optional name-based search filtering.

            **Filtering Options:**
            - **name**: Search by artist name (case-insensitive partial match)

            **Search Features:**
            - Partial matching for flexible search
            - Case-insensitive search
            - Returns all artists if no filter provided

            **Use Cases:**
            - Browse complete artist catalog
            - Search for artists by name
            - Populate artist selection dropdowns
            - Artist management interfaces

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("artists")
        request {
            queryParameter<String>("name") {
                description = "Search by artist name (case-insensitive partial match)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<ArtistsResponse> {
                    example("Artists list") {
                        value =
                            ArtistsResponse(
                                artists =
                                    listOf(
                                        Artist(1, "Dominik Eulberg"),
                                        Artist(2, "Extrawelt"),
                                    ),
                                total = 2,
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun createArtistDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "createArtist"
        summary = "Create Artist"
        description =
            """
            Create a new artist in the catalog.

            **Required Fields:**
            - **name**: Artist name (must be provided and non-empty)

            **Behavior:**
            - Artist ID is automatically generated
            - No duplicate checking (same name allowed for different artists)
            - Immediately available for vinyl associations

            **Use Cases:**
            - Add new artists before creating vinyl records
            - Expand catalog with new artists
            - Support catalog management workflows

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can create artists
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("artists")
        request {
            body<CreateArtistRequest> {
                description = "Artist name to create."
                example("New artist") {
                    value = CreateArtistRequest(name = "Kollektiv Turmstrasse")
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Artist> {
                    example("Created artist") {
                        value = Artist(3, "Kollektiv Turmstrasse")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can create artists")
        }
    }

private fun updateArtistDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "updateArtist"
        summary = "Update Artist"
        description =
            """
            Update an artist's name.

            **Updatable Fields:**
            - **name**: Artist name (required in request)

            **Referential Integrity:**
            - Changes are reflected in all associated vinyls
            - No orphaned references created
            - Existing vinyl associations remain intact

            **Use Cases:**
            - Correct artist name spelling
            - Update artist name changes (e.g., collaborations)
            - Standardize artist naming conventions

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update artists
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("artists")
        request {
            pathParameter<String>("id") {
                description = "Artist ID"
                example("Update artist") {
                    value = "1"
                }
            }
            body<UpdateArtistRequest> {
                example("Update name") {
                    value = UpdateArtistRequest(name = "Donato Dozzy & Nuel")
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Artist> {
                    example("Updated artist") {
                        value = Artist(1, "Donato Dozzy & Nuel")
                    }
                }
            }
            badRequestExample("Invalid artist ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update artists")
            notFoundExample("Artist not found")
        }
    }

private fun deleteArtistDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "deleteArtist"
        summary = "Delete Artist"
        description =
            """
            Permanently delete an artist from the catalog.

            **Referential Integrity Constraints:**
            - Cannot delete artists with associated vinyl records
            - Returns 409 Conflict if artist has vinyls
            - Protects against orphaned vinyl records

            **Best Practices:**
            - Remove or reassign all vinyl records before deletion
            - Verify no vinyls reference this artist
            - Consider keeping artists for historical records

            **Use Cases:**
            - Remove artists created in error
            - Clean up duplicate artist entries
            - Maintain data quality

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can delete artists
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("artists")
        request {
            pathParameter<String>("id") {
                description = "Artist ID"
                example("Delete artist") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Artist deleted successfully"
            }
            badRequestExample("Invalid artist ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can delete artists")
            notFoundExample("Artist not found")
            conflictExample("Artist has vinyls" to "Cannot delete artist with associated vinyls")
        }
    }
