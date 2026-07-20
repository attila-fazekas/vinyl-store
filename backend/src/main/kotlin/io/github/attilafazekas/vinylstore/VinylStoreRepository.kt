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

import io.github.attilafazekas.vinylstore.db.address
import io.github.attilafazekas.vinylstore.db.artist
import io.github.attilafazekas.vinylstore.db.genre
import io.github.attilafazekas.vinylstore.db.inventory
import io.github.attilafazekas.vinylstore.db.label
import io.github.attilafazekas.vinylstore.db.listing
import io.github.attilafazekas.vinylstore.db.user
import io.github.attilafazekas.vinylstore.db.vinyl
import io.github.attilafazekas.vinylstore.db.vinylArtist
import io.github.attilafazekas.vinylstore.db.vinylGenre
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.Address
import io.github.attilafazekas.vinylstore.models.Artist
import io.github.attilafazekas.vinylstore.models.Genre
import io.github.attilafazekas.vinylstore.models.Inventory
import io.github.attilafazekas.vinylstore.models.Label
import io.github.attilafazekas.vinylstore.models.Listing
import io.github.attilafazekas.vinylstore.models.User
import io.github.attilafazekas.vinylstore.models.Vinyl
import io.github.attilafazekas.vinylstore.models.VinylArtist
import io.github.attilafazekas.vinylstore.models.VinylGenre
import io.github.oshai.kotlinlogging.KotlinLogging
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.QueryDsl.Companion.create
import org.komapper.core.dsl.query.firstOrNull
import org.komapper.r2dbc.R2dbcDatabase
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

class VinylStoreRepository(
    private val db: R2dbcDatabase,
) {
    val createdAt = System.currentTimeMillis()

    fun shouldReset(): Boolean {
        val oneHourInMillis = 60 * 60 * 1000
        return System.currentTimeMillis() - createdAt > oneHourInMillis
    }

    suspend fun initialize() {
        db.runQuery {
            create(
                Meta.user,
                Meta.address,
                Meta.artist,
                Meta.genre,
                Meta.label,
                Meta.vinyl,
                Meta.listing,
                Meta.inventory,
                Meta.vinylArtist,
                Meta.vinylGenre,
            )
        }
        val isEmpty = db.runQuery { QueryDsl.from(Meta.user).limit(1) }.isEmpty()
        if (isEmpty) {
            bootstrap()
        }
    }

    suspend fun resetToBootstrap() {
        db.withTransaction {
            db.runQuery { QueryDsl.delete(Meta.inventory).all() }
            db.runQuery { QueryDsl.delete(Meta.listing).all() }
            db.runQuery { QueryDsl.delete(Meta.vinylArtist).all() }
            db.runQuery { QueryDsl.delete(Meta.vinylGenre).all() }
            db.runQuery { QueryDsl.delete(Meta.vinyl).all() }
            db.runQuery { QueryDsl.delete(Meta.label).all() }
            db.runQuery { QueryDsl.delete(Meta.genre).all() }
            db.runQuery { QueryDsl.delete(Meta.artist).all() }
            db.runQuery { QueryDsl.delete(Meta.address).all() }
            db.runQuery { QueryDsl.delete(Meta.user).all() }
        }
        bootstrap()
    }

    private suspend fun bootstrap() {
        db.withTransaction {
            createUser(Email(ADMIN_EMAIL), Password(ADMIN_PASSWORD), Role.ADMIN)
            createUser(Email(STAFF_EMAIL), Password(STAFF_PASSWORD), Role.STAFF)
            createUser(Email(CUSTOMER_EMAIL), Password(CUSTOMER_PASSWORD), Role.CUSTOMER)
            loadVinylsFromCsv()
        }
    }

    private suspend fun loadVinylsFromCsv() {
        val csvContent =
            this::class.java.classLoader
                .getResourceAsStream("collection.csv")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: run {
                    logger.warn { "collection.csv not found in resources" }
                    return
                }

        val lines = csvContent.lines().drop(1).filter { it.isNotBlank() }
        val artistCache = mutableMapOf<String, Artist>()
        val labelCache = mutableMapOf<String, Label>()
        val genreCache = mutableMapOf<String, Genre>()

        for (line in lines) {
            try {
                val parts = parseCsvLine(line)
                if (parts.size < 7) continue

                val artistNames = parts[0].trim()
                val title = parts[1].trim()
                val labelName = parts[2].trim()
                val genreName = parts[3].trim()
                val year = parts[4].trim().toIntOrNull() ?: continue
                val mediaCondition = normalizeCondition(parts[5].trim())
                val sleeveCondition = normalizeCondition(parts[6].trim())

                // Split artist names by "/" for collaborations
                val artistNameList = artistNames.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                if (artistNameList.isEmpty()) continue

                // Get or create all artists
                val artistList =
                    artistNameList.map { artistName ->
                        artistCache.getOrPut(artistName) {
                            createArtist(artistName)
                        }
                    }

                // Use first artist as primary artist
                val primaryArtist = artistList.first()

                // Get or create label
                val label =
                    labelCache.getOrPut(labelName) {
                        createLabel(labelName)
                    }

                // Get or create genre
                val genre =
                    genreCache.getOrPut(genreName) {
                        createGenre(genreName)
                    }

                // Create vinyl with primary artist and genre
                val vinyl =
                    createVinyl(
                        title = title,
                        artistId = primaryArtist.id,
                        labelId = label.id,
                        genreId = genre.id,
                        year = year,
                        conditionMedia = mediaCondition,
                        conditionSleeve = sleeveCondition,
                    )

                // Link all artists to the vinyl
                artistList.forEach { artist ->
                    linkVinylArtist(vinyl.id, artist.id)
                }

                // Link genre to the vinyl
                linkVinylGenre(vinyl.id, genre.id)

                // Create listing with random price and stock
                val price = (15.0 + Math.random() * 85.0).let { (it * 100).toInt() / 100.0 }
                val stock = (5 + (Math.random() * 20).toInt())
                createListing(vinyl.id, price, "EUR", stock)
            } catch (e: Exception) {
                logger.warn { "Warning: Failed to parse CSV line: $line - ${e.message}" }
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when (char) {
                '"' -> {
                    inQuotes = !inQuotes
                }

                ',' if !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }

                else -> {
                    current.append(char)
                }
            }
        }
        result.add(current.toString())
        return result
    }

    private fun normalizeCondition(condition: String): String =
        when {
            condition.startsWith("Mint") -> "M"
            condition.startsWith("Near Mint") -> "NM"
            condition.startsWith("Very Good Plus") -> "VG+"
            condition.startsWith("Very Good") -> "VG"
            condition.startsWith("Good Plus") -> "G+"
            condition.startsWith("Good") -> "G"
            condition.startsWith("No Cover") -> "NC"
            else -> condition
        }

    suspend fun createUser(
        email: Email,
        password: Password,
        role: Role,
    ): User {
        val now = TimestampUtil.now()
        val user =
            User(
                id = Uuid.random(),
                email = email,
                passwordHash = PasswordUtil.hash(password),
                role = role,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            )
        return db.runQuery { QueryDsl.insert(Meta.user).single(user) }
    }

    suspend fun updateUser(
        id: Uuid,
        role: Role?,
        isActive: Boolean?,
    ): User? {
        val user = getUserById(id) ?: return null
        val updated =
            user.copy(
                role = role ?: user.role,
                isActive = isActive ?: user.isActive,
                updatedAt = TimestampUtil.now(),
            )
        return db.runQuery { QueryDsl.update(Meta.user).single(updated) }
    }

    suspend fun deleteUser(id: Uuid): Boolean = db.runQuery { QueryDsl.delete(Meta.user).where { Meta.user.id eq id } } > 0

    suspend fun getUserByEmail(email: Email): User? =
        db.runQuery { QueryDsl.from(Meta.user).where { Meta.user.email eq email }.firstOrNull() }

    suspend fun getUserById(id: Uuid): User? = db.runQuery { QueryDsl.from(Meta.user).where { Meta.user.id eq id }.firstOrNull() }

    suspend fun getAllUsers(): List<User> = db.runQuery { QueryDsl.from(Meta.user).orderBy(Meta.user.id) }

    suspend fun createAddress(
        userId: Uuid,
        type: AddressType,
        fullName: String,
        street: String,
        city: String,
        postalCode: String,
        country: String,
        isDefault: Boolean,
    ): Address {
        if (isDefault) {
            db.runQuery {
                QueryDsl
                    .update(Meta.address)
                    .set { Meta.address.isDefault eq false }
                    .where {
                        Meta.address.userId eq userId
                        Meta.address.type eq type
                    }
            }
        }
        val now = TimestampUtil.now()
        val address =
            Address(
                id = Uuid.random(),
                userId = userId,
                type = type,
                fullName = fullName,
                street = street,
                city = city,
                postalCode = postalCode,
                country = country,
                isDefault = isDefault,
                createdAt = now,
                updatedAt = now,
            )
        return db.runQuery { QueryDsl.insert(Meta.address).single(address) }
    }

    suspend fun getAddressesByUserId(userId: Uuid): Addresses =
        db.runQuery { QueryDsl.from(Meta.address).where { Meta.address.userId eq userId }.orderBy(Meta.address.id) }

    suspend fun getAddressById(id: Uuid): Address? =
        db.runQuery { QueryDsl.from(Meta.address).where { Meta.address.id eq id }.firstOrNull() }

    suspend fun updateAddress(
        id: Uuid,
        type: AddressType?,
        fullName: String?,
        street: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        isDefault: Boolean?,
    ): Address? {
        val address = getAddressById(id) ?: return null
        val updated =
            address.copy(
                type = type ?: address.type,
                fullName = fullName ?: address.fullName,
                street = street ?: address.street,
                city = city ?: address.city,
                postalCode = postalCode ?: address.postalCode,
                country = country ?: address.country,
                isDefault = isDefault ?: address.isDefault,
                updatedAt = TimestampUtil.now(),
            )
        return db.runQuery { QueryDsl.update(Meta.address).single(updated) }
    }

    suspend fun deleteAddress(id: Uuid): Boolean = db.runQuery { QueryDsl.delete(Meta.address).where { Meta.address.id eq id } } > 0

    suspend fun unsetDefaultAddresses(
        userId: Uuid,
        type: AddressType,
    ) {
        db.runQuery {
            QueryDsl
                .update(Meta.address)
                .set {
                    Meta.address.isDefault eq false
                    Meta.address.updatedAt eq TimestampUtil.now()
                }.where {
                    Meta.address.userId eq userId
                    Meta.address.type eq type
                    Meta.address.isDefault eq true
                }
        }
    }

    suspend fun createArtist(name: String): Artist {
        val existing = db.runQuery { QueryDsl.from(Meta.artist) }.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }
        val artist = Artist(Uuid.random(), name)
        return db.runQuery { QueryDsl.insert(Meta.artist).single(artist) }
    }

    suspend fun updateArtist(
        id: Uuid,
        name: String,
    ): Artist? {
        val artist = getArtistById(id) ?: return null
        val updated = artist.copy(name = name)
        return db.runQuery { QueryDsl.update(Meta.artist).single(updated) }
    }

    suspend fun deleteArtist(id: Uuid): Boolean = db.runQuery { QueryDsl.delete(Meta.artist).where { Meta.artist.id eq id } } > 0

    suspend fun getArtistById(id: Uuid): Artist? = db.runQuery { QueryDsl.from(Meta.artist).where { Meta.artist.id eq id }.firstOrNull() }

    suspend fun getAllArtists(): List<Artist> = db.runQuery { QueryDsl.from(Meta.artist).orderBy(Meta.artist.id) }

    suspend fun hasVinylsForArtist(id: Uuid): Boolean =
        db.runQuery { QueryDsl.from(Meta.vinylArtist).where { Meta.vinylArtist.artistId eq id } }.isNotEmpty()

    suspend fun createGenre(name: String): Genre {
        val existing = db.runQuery { QueryDsl.from(Meta.genre) }.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }
        val genre = Genre(Uuid.random(), name)
        return db.runQuery { QueryDsl.insert(Meta.genre).single(genre) }
    }

    suspend fun updateGenre(
        id: Uuid,
        name: String,
    ): Genre? {
        val genre = getGenreById(id) ?: return null
        val updated = genre.copy(name = name)
        return db.runQuery { QueryDsl.update(Meta.genre).single(updated) }
    }

    suspend fun deleteGenre(id: Uuid): Boolean = db.runQuery { QueryDsl.delete(Meta.genre).where { Meta.genre.id eq id } } > 0

    suspend fun getGenreById(id: Uuid): Genre? = db.runQuery { QueryDsl.from(Meta.genre).where { Meta.genre.id eq id }.firstOrNull() }

    suspend fun getAllGenres(): List<Genre> = db.runQuery { QueryDsl.from(Meta.genre).orderBy(Meta.genre.id) }

    suspend fun hasVinylsForGenre(id: Uuid): Boolean =
        db.runQuery { QueryDsl.from(Meta.vinylGenre).where { Meta.vinylGenre.genreId eq id } }.isNotEmpty()

    suspend fun createLabel(name: String): Label {
        val existing = db.runQuery { QueryDsl.from(Meta.label) }.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }
        val label = Label(Uuid.random(), name)
        return db.runQuery { QueryDsl.insert(Meta.label).single(label) }
    }

    suspend fun updateLabel(
        id: Uuid,
        name: String,
    ): Label? {
        val label = getLabelById(id) ?: return null
        val updated = label.copy(name = name)
        return db.runQuery { QueryDsl.update(Meta.label).single(updated) }
    }

    suspend fun deleteLabel(id: Uuid): Boolean = db.runQuery { QueryDsl.delete(Meta.label).where { Meta.label.id eq id } } > 0

    suspend fun getLabelById(id: Uuid): Label? = db.runQuery { QueryDsl.from(Meta.label).where { Meta.label.id eq id }.firstOrNull() }

    suspend fun getAllLabels(): List<Label> = db.runQuery { QueryDsl.from(Meta.label).orderBy(Meta.label.id) }

    suspend fun hasVinylsForLabel(id: Uuid): Boolean =
        db.runQuery { QueryDsl.from(Meta.vinyl).where { Meta.vinyl.labelId eq id } }.isNotEmpty()

    suspend fun createVinyl(
        title: String,
        artistId: Uuid,
        labelId: Uuid,
        genreId: Uuid,
        year: Int,
        conditionMedia: String,
        conditionSleeve: String,
    ): Vinyl {
        val now = TimestampUtil.now()
        val vinyl =
            Vinyl(
                id = Uuid.random(),
                title = title,
                artistId = artistId,
                labelId = labelId,
                genreId = genreId,
                year = year,
                conditionMedia = conditionMedia,
                conditionSleeve = conditionSleeve,
                createdAt = now,
                updatedAt = now,
            )
        return db.runQuery { QueryDsl.insert(Meta.vinyl).single(vinyl) }
    }

    suspend fun updateVinyl(
        id: Uuid,
        title: String?,
        artistId: Uuid?,
        labelId: Uuid?,
        year: Int?,
        conditionMedia: String?,
        conditionSleeve: String?,
    ): Vinyl? {
        val vinyl = getVinylById(id) ?: return null
        val updated =
            vinyl.copy(
                title = title ?: vinyl.title,
                artistId = artistId ?: vinyl.artistId,
                labelId = labelId ?: vinyl.labelId,
                year = year ?: vinyl.year,
                conditionMedia = conditionMedia ?: vinyl.conditionMedia,
                conditionSleeve = conditionSleeve ?: vinyl.conditionSleeve,
                updatedAt = TimestampUtil.now(),
            )
        return db.runQuery { QueryDsl.update(Meta.vinyl).single(updated) }
    }

    suspend fun deleteVinyl(id: Uuid): Boolean =
        db.withTransaction {
            db.runQuery { QueryDsl.delete(Meta.vinylArtist).where { Meta.vinylArtist.vinylId eq id } }
            db.runQuery { QueryDsl.delete(Meta.vinylGenre).where { Meta.vinylGenre.vinylId eq id } }
            db.runQuery { QueryDsl.delete(Meta.vinyl).where { Meta.vinyl.id eq id } } > 0
        }

    suspend fun linkVinylArtist(
        vinylId: Uuid,
        artistId: Uuid,
    ) {
        db.runQuery { QueryDsl.insert(Meta.vinylArtist).single(VinylArtist(vinylId, artistId)) }
    }

    suspend fun linkVinylGenre(
        vinylId: Uuid,
        genreId: Uuid,
    ) {
        db.runQuery { QueryDsl.insert(Meta.vinylGenre).single(VinylGenre(vinylId, genreId)) }
    }

    suspend fun unlinkAllVinylGenres(vinylId: Uuid) {
        db.runQuery { QueryDsl.delete(Meta.vinylGenre).where { Meta.vinylGenre.vinylId eq vinylId } }
    }

    suspend fun getGenreForVinyl(vinylId: Uuid): Genre? {
        val genreId =
            db
                .runQuery { QueryDsl.from(Meta.vinylGenre).where { Meta.vinylGenre.vinylId eq vinylId } }
                .firstOrNull()
                ?.genreId
        return genreId?.let { getGenreById(it) }
    }

    suspend fun getArtistsForVinyl(vinylId: Uuid): List<Artist> {
        val artistIds =
            db
                .runQuery { QueryDsl.from(Meta.vinylArtist).where { Meta.vinylArtist.vinylId eq vinylId } }
                .map { it.artistId }
        if (artistIds.isEmpty()) return emptyList()
        return db.runQuery { QueryDsl.from(Meta.artist).where { Meta.artist.id inList artistIds }.orderBy(Meta.artist.id) }
    }

    suspend fun getVinylById(id: Uuid): Vinyl? = db.runQuery { QueryDsl.from(Meta.vinyl).where { Meta.vinyl.id eq id }.firstOrNull() }

    suspend fun getAllVinyls(): List<Vinyl> = db.runQuery { QueryDsl.from(Meta.vinyl).orderBy(Meta.vinyl.id) }

    suspend fun hasListingsForVinyl(id: Uuid): Boolean =
        db.runQuery { QueryDsl.from(Meta.listing).where { Meta.listing.vinylId eq id } }.isNotEmpty()

    suspend fun createListing(
        vinylId: Uuid,
        price: Double,
        currency: String,
        initialStock: Int,
    ): Listing {
        val now = TimestampUtil.now()
        val listing = Listing(Uuid.random(), vinylId, ListingStatus.PUBLISHED, price, currency, now, now)
        val insertedListing = db.runQuery { QueryDsl.insert(Meta.listing).single(listing) }

        val newInventory =
            Inventory(
                id = Uuid.random(),
                listingId = insertedListing.id,
                totalQuantity = initialStock,
                reservedQuantity = 0,
                createdAt = now,
                updatedAt = now,
            )
        db.runQuery { QueryDsl.insert(Meta.inventory).single(newInventory) }
        return insertedListing
    }

    suspend fun updateListing(
        id: Uuid,
        price: Double?,
        status: ListingStatus?,
    ): Listing? {
        val existing = getListingById(id) ?: return null
        val updated =
            existing.copy(
                price = price ?: existing.price,
                status = status ?: existing.status,
                updatedAt = TimestampUtil.now(),
            )
        return db.runQuery { QueryDsl.update(Meta.listing).single(updated) }
    }

    suspend fun getListingById(id: Uuid): Listing? =
        db.runQuery { QueryDsl.from(Meta.listing).where { Meta.listing.id eq id }.firstOrNull() }

    suspend fun getAllPublishedListings(): Listings =
        db.runQuery { QueryDsl.from(Meta.listing).where { Meta.listing.status eq ListingStatus.PUBLISHED }.orderBy(Meta.listing.id) }

    suspend fun deleteListing(id: Uuid): Boolean =
        db.withTransaction {
            db.runQuery { QueryDsl.delete(Meta.inventory).where { Meta.inventory.listingId eq id } }
            db.runQuery { QueryDsl.delete(Meta.listing).where { Meta.listing.id eq id } } > 0
        }

    suspend fun hasActiveOrders(listingId: Uuid): Boolean {
        // TODO: Implement when orders are added
        // For now, return false to allow deletion
        return false
    }

    suspend fun getInventoryByListingId(listingId: Uuid): Inventory? =
        db.runQuery { QueryDsl.from(Meta.inventory).where { Meta.inventory.listingId eq listingId }.firstOrNull() }

    suspend fun getAllInventory(): List<Inventory> = db.runQuery { QueryDsl.from(Meta.inventory).orderBy(Meta.inventory.id) }

    suspend fun updateInventory(
        listingId: Uuid,
        totalQuantity: Int?,
        reservedQuantity: Int?,
    ): Inventory? {
        val existing = getInventoryByListingId(listingId) ?: return null
        val updated =
            Inventory(
                id = existing.id,
                listingId = existing.listingId,
                totalQuantity = totalQuantity ?: existing.totalQuantity,
                reservedQuantity = reservedQuantity ?: existing.reservedQuantity,
                createdAt = existing.createdAt,
                updatedAt = TimestampUtil.now(),
            )
        return db.runQuery { QueryDsl.update(Meta.inventory).single(updated) }
    }
}
