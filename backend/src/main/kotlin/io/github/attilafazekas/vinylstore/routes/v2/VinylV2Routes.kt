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
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V2
import io.github.attilafazekas.vinylstore.VinylStoreData
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.VinylWithDetailsV2
import io.github.attilafazekas.vinylstore.models.VinylsV2Response
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.text.toIntOrNull

fun Route.vinylV2Routes(store: VinylStoreData) {
    authenticate(AUTH_JWT) {
        route("$V2/vinyls") {
            get(listVinylsV2Documentation()) {
                val artistParam = call.parameters["artist"]
                val genreParam = call.parameters["genre"]
                val labelParam = call.parameters["label"]
                val yearParam = call.parameters["year"]?.toIntOrNull()
                val minYearParam = call.parameters["minYear"]?.toIntOrNull()
                val maxYearParam = call.parameters["maxYear"]?.toIntOrNull()
                val titleParam = call.parameters["title"]

                var vinylsWithDetails =
                    store.vinyls.values.sortedBy { it.id }.mapNotNull { vinyl ->
                        val artists = store.getArtistsForVinyl(vinyl.id)
                        if (artists.isEmpty()) return@mapNotNull null
                        val label = store.labels[vinyl.labelId] ?: return@mapNotNull null
                        val genre = store.getGenreForVinyl(vinyl.id) ?: return@mapNotNull null

                        VinylWithDetailsV2(
                            id = vinyl.id,
                            title = vinyl.title,
                            artists = artists,
                            label = label,
                            genre = genre,
                            year = vinyl.year,
                            conditionMedia = vinyl.conditionMedia,
                            conditionSleeve = vinyl.conditionSleeve,
                            createdAt = TimestampUtil.now(),
                            updatedAt = TimestampUtil.now(),
                        )
                    }

                artistParam?.let { artist ->
                    val artistId = artist.toIntOrNull()
                    vinylsWithDetails =
                        vinylsWithDetails.filter { vinyl ->
                            if (artistId != null) {
                                vinyl.artists.any { it.id == artistId }
                            } else {
                                vinyl.artists.any { it.name.contains(artist, ignoreCase = true) }
                            }
                        }
                }

                genreParam?.let { genreFilter ->
                    vinylsWithDetails =
                        vinylsWithDetails.filter { vinyl ->
                            vinyl.genre.name.contains(genreFilter, ignoreCase = true)
                        }
                }

                labelParam?.let { label ->
                    val labelId = label.toIntOrNull()
                    vinylsWithDetails =
                        vinylsWithDetails.filter { vinyl ->
                            if (labelId != null) {
                                vinyl.label.id == labelId
                            } else {
                                vinyl.label.name.contains(label, ignoreCase = true)
                            }
                        }
                }

                // Apply year filters (exact year takes precedence)
                yearParam?.let { year ->
                    vinylsWithDetails = vinylsWithDetails.filter { it.year == year }
                } ?: run {
                    // Apply year range filters if exact year not specified
                    minYearParam?.let { minYear ->
                        vinylsWithDetails = vinylsWithDetails.filter { it.year >= minYear }
                    }
                    maxYearParam?.let { maxYear ->
                        vinylsWithDetails = vinylsWithDetails.filter { it.year <= maxYear }
                    }
                }

                titleParam?.let { title ->
                    vinylsWithDetails = vinylsWithDetails.filter { it.title.contains(title, ignoreCase = true) }
                }

                call.respond(
                    VinylsV2Response(
                        vinyls = vinylsWithDetails,
                        total = vinylsWithDetails.size,
                    ),
                )
            }
        }
    }
}

private fun listVinylsV2Documentation(): RouteConfig.() -> Unit =
    {
        operationId = "listVinylsV2"
        summary = "List Vinyls (V2)"
        description =
            """
            Retrieve all vinyl records with fully embedded artist, label, and genre details.

            **V2 Enhancements:**
            Unlike v1 which returns ID references, v2 embeds complete artist, label, and genre objects
            in each vinyl record, eliminating the need for multiple API calls.

            **Filtering Options:**
            - **artist**: Filter by artist name (partial match) or exact artist ID
            - **genre**: Filter by genre name (partial match)
            - **label**: Filter by label name (partial match) or exact label ID
            - **year**: Filter by exact release year
            - **minYear/maxYear**: Filter by release year range
            - **title**: Search in title (case-insensitive partial match)

            **Embedded Data:**
            - Complete artist objects array (supports multiple artists for collaborations)
            - Complete label object (not just labelId)
            - Complete genre object (not just genreId)

            **Use Cases:**
            - Optimized for mobile apps with limited network requests
            - Reduced latency for catalog browsing
            - Complete data in single response

            **Access Requirements:**
            - Requires authentication via JWT token
            - Accessible to all authenticated roles (CUSTOMER, STAFF, ADMIN)
            """.trimIndent()
        tags = listOf("vinyls-v2")
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
                body<VinylsV2Response> {
                    example("Vinyls with embedded details") {
                        value =
                            VinylsV2Response(
                                vinyls =
                                    listOf(
                                        VinylWithDetailsV2(
                                            id = 1,
                                            title = "Avichrom",
                                            artists = listOf(Artist(1, "Dominik Eulberg")),
                                            label = Label(1, "!K7 Records"),
                                            genre = Genre(1, "Electronic"),
                                            year = 2022,
                                            conditionMedia = "M",
                                            conditionSleeve = "M",
                                            createdAt = TimestampUtil.now(),
                                            updatedAt = TimestampUtil.now(),
                                        ),
                                        VinylWithDetailsV2(
                                            id = 3,
                                            title = "...A Little Further",
                                            artists =
                                                listOf(
                                                    Artist(1, "Dominik Eulberg"),
                                                    Artist(2, "Extrawelt"),
                                                ),
                                            label = Label(2, "Cocoon Recordings"),
                                            genre = Genre(1, "Electronic"),
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
                    example("Empty results") {
                        value =
                            VinylsV2Response(
                                vinyls = emptyList(),
                                total = 0,
                            )
                    }
                }
            }
            badRequestExample("Invalid filter value")
            notAuthenticatedExample()
        }
    }
