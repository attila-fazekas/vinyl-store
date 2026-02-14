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
import io.github.attilafazekas.vinylstore.models.CreateGenreRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.GenresResponse
import io.github.attilafazekas.vinylstore.models.UpdateGenreRequest
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

fun Route.genreRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/genres") {
            get(listGenresDocumentation()) {
                val nameParam = call.parameters["name"]

                var genres = store.genres.values.sortedBy { it.id }

                nameParam?.let { name ->
                    genres = genres.filter { it.name.contains(name, ignoreCase = true) }
                }

                call.respond(GenresResponse(genres, genres.size))
            }

            post(createGenreDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)
                val request = call.receive<CreateGenreRequest>()
                val genre = store.createGenre(request.name)
                call.respond(HttpStatusCode.Created, genre)
            }

            get("/{id}", getGenreDocumentation()) {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid genre ID"))
                    return@get
                }

                val genre = store.genres[id]
                if (genre == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Genre not found"))
                } else {
                    call.respond(genre)
                }
            }

            put("/{id}", updateGenreDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid genre ID"))
                    return@put
                }

                val request = call.receive<UpdateGenreRequest>()
                val updated = store.updateGenre(id, request.name)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Genre not found"))
                } else {
                    call.respond(updated)
                }
            }

            delete("/{id}", deleteGenreDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid genre ID"))
                    return@delete
                }

                if (store.genres[id] == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Genre not found"))
                    return@delete
                }

                val hasVinyls = store.vinylGenres.values.any { it.genreId == id }
                if (hasVinyls) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Cannot delete genre associated with vinyls"),
                    )
                    return@delete
                }

                store.deleteGenre(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun listGenresDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "listGenres"
        summary = "List Genres"
        description =
            """
            Retrieve all music genres in the catalog with optional name-based search filtering.

            **Filtering Options:**
            - **name**: Search by genre name (case-insensitive partial match)

            **Search Features:**
            - Partial matching for flexible search
            - Case-insensitive search
            - Returns all genres if no filter provided

            **Use Cases:**
            - Browse complete genre catalog
            - Search for specific genres
            - Populate genre selection dropdowns
            - Genre filtering in vinyl searches

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("genres")
        request {
            queryParameter<String>("name") {
                description = "Search by genre name (case-insensitive partial match)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<GenresResponse> {
                    example("Genres list") {
                        value =
                            GenresResponse(
                                genres =
                                    listOf(
                                        Genre(1, "Electronic"),
                                        Genre(2, "Hip Hop"),
                                    ),
                                total = 2,
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun createGenreDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "createGenre"
        summary = "Create Genre"
        description =
            """
            Create a new music genre in the catalog.

            **Required Fields:**
            - **name**: Genre name (must be provided and non-empty)

            **Behavior:**
            - Genre ID is automatically generated
            - No duplicate checking (same name allowed)
            - Immediately available for vinyl associations

            **Use Cases:**
            - Expand genre taxonomy
            - Add new music genre classifications
            - Support evolving music categorization

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can create genres
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("genres")
        request {
            body<CreateGenreRequest> {
                description = "Genre name to create."
                example("New genre") {
                    value = CreateGenreRequest(name = "Hip Hop")
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Genre> {
                    example("Created genre") {
                        value = Genre(2, "Hip Hop")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can create genres")
        }
    }

private fun getGenreDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getGenre"
        summary = "Get Genre"
        description =
            """
            Retrieve details of a specific genre by its unique ID.

            **Returns:**
            - Genre ID
            - Genre name

            **Use Cases:**
            - Display genre information
            - Verify genre existence before associations
            - Genre detail pages

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("genres")
        request {
            pathParameter<String>("id") {
                description = "Genre ID"
                example("Genre details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Genre> {
                    example("Genre details") {
                        value = Genre(1, "Electronic")
                    }
                }
            }
            badRequestExample("Invalid genre ID")
            notAuthenticatedExample()
            notFoundExample("Genre not found")
        }
    }

private fun updateGenreDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "updateGenre"
        summary = "Update Genre"
        description =
            """
            Update a genre's name.

            **Updatable Fields:**
            - **name**: Genre name (required in request)

            **Referential Integrity:**
            - Changes are reflected in all associated vinyls
            - No orphaned references created
            - Existing vinyl-genre associations remain intact

            **Use Cases:**
            - Correct genre name spelling
            - Refine genre taxonomy
            - Standardize genre naming conventions

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update genres
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("genres")
        request {
            pathParameter<String>("id") {
                description = "Genre ID"
                example("Update genre") {
                    value = "1"
                }
            }
            body<UpdateGenreRequest> {
                example("Update name") {
                    value = UpdateGenreRequest(name = "Jazz")
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Genre> {
                    example("Updated genre") {
                        value = Genre(1, "Jazz")
                    }
                }
            }
            badRequestExample("Invalid genre ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update genres")
            notFoundExample("Genre not found")
        }
    }

private fun deleteGenreDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "deleteGenre"
        summary = "Delete Genre"
        description =
            """
            Permanently delete a genre from the catalog.

            **Referential Integrity Constraints:**
            - Cannot delete genres associated with vinyl records
            - Returns 409 Conflict if genre has vinyl associations
            - Protects against orphaned vinyl-genre links

            **Best Practices:**
            - Remove genre associations from all vinyls before deletion
            - Verify no vinyls reference this genre
            - Consider keeping genres for consistent taxonomy

            **Use Cases:**
            - Remove genres created in error
            - Clean up duplicate or obsolete genre entries
            - Maintain genre taxonomy quality

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can delete genres
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("genres")
        request {
            pathParameter<String>("id") {
                description = "Genre ID"
                example("Delete genre") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Genre deleted successfully"
            }
            badRequestExample("Invalid genre ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can delete genres")
            notFoundExample("Genre not found")
            conflictExample("Genre associated with vinyls" to "Cannot delete genre associated with vinyls")
        }
    }
