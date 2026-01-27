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
import io.github.attilafazekas.vinylstore.models.CreateLabelRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.LabelsResponse
import io.github.attilafazekas.vinylstore.models.UpdateLabelRequest
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

fun Route.labelRoutes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V1/labels") {
            get(listLabelsDocumentation()) {
                val nameParam = call.parameters["name"]

                var labels = store.labels.values.sortedBy { it.id }

                nameParam?.let { name ->
                    labels = labels.filter { it.name.contains(name, ignoreCase = true) }
                }

                call.respond(LabelsResponse(labels, labels.size))
            }

            post(createLabelDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)
                val request = call.receive<CreateLabelRequest>()
                val label = store.createLabel(request.name)
                call.respond(HttpStatusCode.Created, label)
            }

            get("/{id}", getLabelDocumentation()) {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid label ID"))
                    return@get
                }

                val label = store.labels[id]
                if (label == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Label not found"))
                } else {
                    call.respond(label)
                }
            }

            put("/{id}", updateLabelDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid label ID"))
                    return@put
                }

                val request = call.receive<UpdateLabelRequest>()
                val updated = store.updateLabel(id, request.name)

                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Label not found"))
                } else {
                    call.respond(updated)
                }
            }

            delete("/{id}", deleteLabelDocumentation()) {
                call.requireRole(Role.ADMIN, Role.STAFF)

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid label ID"))
                    return@delete
                }

                if (store.labels[id] == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Label not found"))
                    return@delete
                }

                val hasVinyls = store.vinyls.values.any { it.labelId == id }
                if (hasVinyls) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(CONFLICT, "Cannot delete label with associated vinyls"),
                    )
                    return@delete
                }

                store.deleteLabel(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun deleteLabelDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Delete Label"
        description =
            """
            Permanently delete a record label from the catalog.

            **Referential Integrity Constraints:**
            - Cannot delete labels with associated vinyl records
            - Returns 409 Conflict if label has vinyls
            - Protects against orphaned vinyl records

            **Best Practices:**
            - Remove or reassign all vinyl records before deletion
            - Verify no vinyls reference this label
            - Consider keeping labels for historical records

            **Use Cases:**
            - Remove labels created in error
            - Clean up duplicate label entries
            - Maintain label catalog quality

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can delete labels
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("labels")
        request {
            pathParameter<String>("id") {
                description = "Label ID"
                example("Delete label") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Label deleted successfully"
            }
            badRequestExample("Invalid label ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can delete labels")
            notFoundExample("Label not found")
            conflictExample("Label has vinyls" to "Cannot delete label with associated vinyls")
        }
    }

private fun listLabelsDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "List Labels"
        description =
            """
            Retrieve all record labels in the catalog with optional name-based search filtering.

            **Filtering Options:**
            - **name**: Search by label name (case-insensitive partial match)

            **Search Features:**
            - Partial matching for flexible search
            - Case-insensitive search
            - Returns all labels if no filter provided

            **Use Cases:**
            - Browse complete label catalog
            - Search for specific record labels
            - Populate label selection dropdowns
            - Label filtering in vinyl searches

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("labels")
        request {
            queryParameter<String>("name") {
                description = "Search by label name (case-insensitive partial match)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<LabelsResponse> {
                    example("Labels list") {
                        value =
                            LabelsResponse(
                                labels =
                                    listOf(
                                        Label(1, "Further Records"),
                                        Label(2, "!K7 Records"),
                                    ),
                                total = 2,
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun createLabelDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Create Label"
        description =
            """
            Create a new record label in the catalog.

            **Required Fields:**
            - **name**: Label name (must be provided and non-empty)

            **Behavior:**
            - Label ID is automatically generated
            - No duplicate checking (same name allowed)
            - Immediately available for vinyl associations

            **Use Cases:**
            - Add new record labels before creating vinyl records
            - Expand label catalog
            - Support independent and new labels

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can create labels
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("labels")
        request {
            body<CreateLabelRequest> {
                description = "Label name to create."
                example("New label") {
                    value = CreateLabelRequest(name = "Spazio Disponibile")
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<Label> {
                    example("Created label") {
                        value = Label(3, "Spazio Disponibile")
                    }
                }
            }
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can create labels")
        }
    }

private fun updateLabelDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Update Label"
        description =
            """
            Update a record label's name.

            **Updatable Fields:**
            - **name**: Label name (required in request)

            **Referential Integrity:**
            - Changes are reflected in all associated vinyls
            - No orphaned references created
            - Existing vinyl associations remain intact

            **Use Cases:**
            - Correct label name spelling
            - Update label rebranding
            - Standardize label naming conventions

            **Access Requirements:**
            - Requires authentication via JWT token
            - Only ADMIN and STAFF roles can update labels
            - Returns 403 Forbidden for CUSTOMER role
            """.trimIndent()
        tags = listOf("labels")
        request {
            pathParameter<String>("id") {
                description = "Label ID"
                example("Update label") {
                    value = "1"
                }
            }
            body<UpdateLabelRequest> {
                example("Update name") {
                    value = UpdateLabelRequest(name = "Further Records Ltd")
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Label> {
                    example("Updated label") {
                        value = Label(1, "Further Records Ltd")
                    }
                }
            }
            badRequestExample("Invalid label ID")
            notAuthenticatedExample()
            insufficientPermissionsExample("Only ADMIN and STAFF roles can update labels")
            notFoundExample("Label not found")
        }
    }

private fun getLabelDocumentation(): RouteConfig.() -> Unit =
    {
        summary = "Get Label"
        description =
            """
            Retrieve details of a specific record label by its unique ID.

            **Returns:**
            - Label ID
            - Label name

            **Use Cases:**
            - Display label information
            - Verify label existence before associations
            - Label detail pages

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("labels")
        request {
            pathParameter<String>("id") {
                description = "Label ID"
                example("Label details") {
                    value = "1"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<Label> {
                    example("Label details") {
                        value = Label(1, "!K7 Records")
                    }
                }
            }
            badRequestExample("Invalid label ID")
            notAuthenticatedExample()
            notFoundExample("Label not found")
        }
    }
