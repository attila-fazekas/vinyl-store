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

package io.github.attilafazekas.vinylstore

import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.komapper.r2dbc.R2dbcDatabase
import kotlin.uuid.Uuid

class CatalogDeletionRoutesTest {
    @Test
    fun `delete catalog references rejects all associated vinyl references and keeps listing readable`() {
        withCatalogDeletionFixture { client, token, fixture ->
            val genreDelete = client.deleteWithToken("/v1/genres/${fixture.genreId}", token)
            genreDelete.status shouldBe HttpStatusCode.Conflict
            genreDelete.body<ErrorResponse>() shouldBe
                ErrorResponse(CONFLICT, "Cannot delete genre associated with vinyls")

            val labelDelete = client.deleteWithToken("/v1/labels/${fixture.labelId}", token)
            labelDelete.status shouldBe HttpStatusCode.Conflict
            labelDelete.body<ErrorResponse>() shouldBe
                ErrorResponse(CONFLICT, "Cannot delete label with associated vinyls")

            val artistDelete = client.deleteWithToken("/v1/artists/${fixture.artistId}", token)
            artistDelete.status shouldBe HttpStatusCode.Conflict
            artistDelete.body<ErrorResponse>() shouldBe
                ErrorResponse(CONFLICT, "Cannot delete artist with associated vinyls")

            client.get("/v1/listings/${fixture.listingId}").status shouldBe HttpStatusCode.OK
        }
    }

    private fun withCatalogDeletionFixture(block: suspend (HttpClient, String, CatalogDeletionFixture) -> Unit) {
        testApplication {
            val store =
                VinylStoreRepository(
                    R2dbcDatabase("r2dbc:postgresql://vinylstore:vinylstore@localhost/vinylstore"),
                )
            application { vinylStoreApplication(store = store) }

            val admin =
                store.createUser(
                    Email("admin-${Uuid.random()}@example.com"),
                    Password("password123"),
                    Role.Admin,
                )
            val token = JwtConfig.generateToken(admin.id, admin.email, admin.role)
            val fixture = store.createCatalogDeletionFixture()
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    install(DefaultRequest) {
                        contentType(ContentType.Application.Json)
                    }
                }

            block(client, token, fixture)
        }
    }

    private suspend fun VinylStoreRepository.createCatalogDeletionFixture(): CatalogDeletionFixture {
        val suffix = Uuid.random()
        val artist = createArtist("Deletion Artist $suffix")
        val label = createLabel("Deletion Label $suffix")
        val genre = createGenre("Deletion Genre $suffix")
        val vinyl = createVinyl("Deletion Vinyl $suffix", artist.id, label.id, genre.id, 2020, "M", "M")
        linkVinylGenre(vinyl.id, genre.id)
        val listing = createListing(vinyl.id, 19.99, "EUR", 1)
        return CatalogDeletionFixture(artist.id, label.id, genre.id, listing.id)
    }

    private suspend fun HttpClient.deleteWithToken(
        url: String,
        token: String,
    ) = delete(url) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private data class CatalogDeletionFixture(
        val artistId: Uuid,
        val labelId: Uuid,
        val genreId: Uuid,
        val listingId: Uuid,
    )
}
