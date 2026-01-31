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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.set

private val logger = KotlinLogging.logger {}

class VinylStoreData {
    val users = ConcurrentHashMap<Int, User>()
    val addresses = ConcurrentHashMap<Int, Address>()
    val artists = ConcurrentHashMap<Int, Artist>()
    val genres = ConcurrentHashMap<Int, Genre>()
    val labels = ConcurrentHashMap<Int, Label>()
    val vinyls = ConcurrentHashMap<Int, Vinyl>()
    val vinylArtists = ConcurrentHashMap<String, VinylArtist>()
    val vinylGenres = ConcurrentHashMap<String, VinylGenre>()
    val listings = ConcurrentHashMap<Int, Listing>()
    val inventory = ConcurrentHashMap<Int, Inventory>()

    val userIdCounter = AtomicInteger(1)
    val addressIdCounter = AtomicInteger(1)
    val artistIdCounter = AtomicInteger(1)
    val genreIdCounter = AtomicInteger(1)
    val labelIdCounter = AtomicInteger(1)
    val vinylIdCounter = AtomicInteger(1)
    val listingIdCounter = AtomicInteger(1)
    val inventoryIdCounter = AtomicInteger(1)

    val createdAt = System.currentTimeMillis()

    init {
        bootstrap()
    }

    fun shouldReset(): Boolean {
        val oneHourInMillis = 60 * 60 * 1000
        return System.currentTimeMillis() - createdAt > oneHourInMillis
    }

    fun resetToBootstrap() {
        users.clear()
        addresses.clear()
        artists.clear()
        genres.clear()
        labels.clear()
        vinyls.clear()
        vinylGenres.clear()
        listings.clear()
        inventory.clear()

        userIdCounter.set(1)
        addressIdCounter.set(1)
        artistIdCounter.set(1)
        genreIdCounter.set(1)
        labelIdCounter.set(1)
        vinylIdCounter.set(1)
        listingIdCounter.set(1)
        inventoryIdCounter.set(1)

        bootstrap()
    }

    private fun bootstrap() {
        createUser(Email("admin@vinylstore.com"), Password("admin123"), Role.ADMIN)
        createUser(Email("staff@vinylstore.com"), Password("staff123"), Role.STAFF)
        loadVinylsFromCsv()
    }

    private fun loadVinylsFromCsv() {
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

    fun createUser(
        email: Email,
        password: Password,
        role: Role,
    ): User {
        val id = userIdCounter.getAndIncrement()
        val user =
            User(
                id = id,
                email = email,
                passwordHash = PasswordUtil.hash(password),
                role = role,
                isActive = true,
                createdAt = TimestampUtil.now(),
                updatedAt = TimestampUtil.now(),
            )
        users[id] = user
        return user
    }

    fun updateUser(
        id: Int,
        role: Role?,
        isActive: Boolean?,
    ): User? {
        val user = users[id] ?: return null
        val updated =
            user.copy(
                role = role ?: user.role,
                isActive = isActive ?: user.isActive,
                updatedAt = TimestampUtil.now(),
            )
        users[id] = updated
        return updated
    }

    fun deleteUser(id: Int): Boolean = users.remove(id) != null

    fun getUserByEmail(email: Email): User? = users.values.find { it.email == email }

    fun getUserById(id: Int): User? = users[id]

    fun getAllUsers(): List<User> = users.values.sortedBy { it.id }

    fun createAddress(
        userId: Int,
        type: AddressType,
        fullName: String,
        street: String,
        city: String,
        postalCode: String,
        country: String,
        isDefault: Boolean,
    ): Address {
        if (isDefault) {
            addresses.values.filter { it.userId == userId && it.type == type }.forEach {
                addresses[it.id] = it.copy(isDefault = false)
            }
        }
        val id = addressIdCounter.getAndIncrement()
        val address =
            Address(
                id = id,
                userId = userId,
                type = type,
                fullName = fullName,
                street = street,
                city = city,
                postalCode = postalCode,
                country = country,
                isDefault = isDefault,
                createdAt = TimestampUtil.now(),
                updatedAt = TimestampUtil.now(),
            )
        addresses[id] = address
        return address
    }

    fun getAddressesByUserId(userId: Int): Addresses = addresses.values.filter { it.userId == userId }.sortedBy { it.id }

    fun getAddressById(id: Int): Address? = addresses[id]

    fun updateAddress(
        id: Int,
        type: AddressType?,
        fullName: String?,
        street: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        isDefault: Boolean?,
    ): Address? {
        val address = addresses[id] ?: return null
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
        addresses[id] = updated
        return updated
    }

    fun deleteAddress(id: Int): Boolean = addresses.remove(id) != null

    fun unsetDefaultAddresses(
        userId: Int,
        type: AddressType,
    ) {
        addresses.values
            .filter { it.userId == userId && it.type == type && it.isDefault }
            .forEach { addresses[it.id] = it.copy(isDefault = false, updatedAt = TimestampUtil.now()) }
    }

    fun createArtist(name: String): Artist {
        // Check if artist with this name already exists (case-insensitive)
        val existing = artists.values.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }

        val id = artistIdCounter.getAndIncrement()
        val artist = Artist(id, name)
        artists[id] = artist
        return artist
    }

    fun updateArtist(
        id: Int,
        name: String,
    ): Artist? {
        val artist = artists[id] ?: return null
        val updated = artist.copy(name = name)
        artists[id] = updated
        return updated
    }

    fun deleteArtist(id: Int): Boolean = artists.remove(id) != null

    fun createGenre(name: String): Genre {
        // Check if genre with this name already exists (case-insensitive)
        val existing = genres.values.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }

        val id = genreIdCounter.getAndIncrement()
        val genre = Genre(id, name)
        genres[id] = genre
        return genre
    }

    fun updateGenre(
        id: Int,
        name: String,
    ): Genre? {
        val genre = genres[id] ?: return null
        val updated = genre.copy(name = name)
        genres[id] = updated
        return updated
    }

    fun deleteGenre(id: Int): Boolean = genres.remove(id) != null

    fun createLabel(name: String): Label {
        // Check if label with this name already exists (case-insensitive)
        val existing = labels.values.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return existing
        }

        val id = labelIdCounter.getAndIncrement()
        val label = Label(id, name)
        labels[id] = label
        return label
    }

    fun updateLabel(
        id: Int,
        name: String,
    ): Label? {
        val label = labels[id] ?: return null
        val updated = label.copy(name = name)
        labels[id] = updated
        return updated
    }

    fun deleteLabel(id: Int): Boolean = labels.remove(id) != null

    fun createVinyl(
        title: String,
        artistId: Int,
        labelId: Int,
        genreId: Int,
        year: Int,
        conditionMedia: String,
        conditionSleeve: String,
    ): Vinyl {
        val id = vinylIdCounter.getAndIncrement()
        val vinyl =
            Vinyl(
                id = id,
                title = title,
                artistId = artistId,
                labelId = labelId,
                genreId = genreId,
                year = year,
                conditionMedia = conditionMedia,
                conditionSleeve = conditionSleeve,
                createdAt = TimestampUtil.now(),
                updatedAt = TimestampUtil.now(),
            )
        vinyls[id] = vinyl
        return vinyl
    }

    fun updateVinyl(
        id: Int,
        title: String?,
        artistId: Int?,
        labelId: Int?,
        year: Int?,
        conditionMedia: String?,
        conditionSleeve: String?,
    ): Vinyl? {
        val vinyl = vinyls[id] ?: return null
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
        vinyls[id] = updated
        return updated
    }

    fun deleteVinyl(id: Int): Boolean {
        // Also remove associated vinyl-genre links
        vinylGenres.entries.removeIf { it.value.vinylId == id }
        return vinyls.remove(id) != null
    }

    fun linkVinylArtist(
        vinylId: Int,
        artistId: Int,
    ) {
        vinylArtists["$vinylId-$artistId"] = VinylArtist(vinylId, artistId)
    }

    fun linkVinylGenre(
        vinylId: Int,
        genreId: Int,
    ) {
        vinylGenres["$vinylId-$genreId"] = VinylGenre(vinylId, genreId)
    }

    fun unlinkAllVinylGenres(vinylId: Int) {
        vinylGenres.entries.removeIf { it.value.vinylId == vinylId }
    }

    fun getGenreForVinyl(vinylId: Int): Genre? {
        val genreId = vinylGenres.values.firstOrNull { it.vinylId == vinylId }?.genreId
        return genreId?.let { genres[it] }
    }

    fun getArtistsForVinyl(vinylId: Int): List<Artist> {
        val artistIds =
            vinylArtists.values
                .filter { it.vinylId == vinylId }
                .map { it.artistId }
        return artistIds.mapNotNull { artists[it] }.sortedBy { it.id }
    }

    fun createListing(
        vinylId: Int,
        price: Double,
        currency: String,
        initialStock: Int,
    ): Listing {
        val id = listingIdCounter.getAndIncrement()
        val now = TimestampUtil.now()
        val listing = Listing(id, vinylId, ListingStatus.PUBLISHED, price, currency, now, now)
        listings[id] = listing

        val inventoryId = inventoryIdCounter.getAndIncrement()
        inventory[id] = Inventory(inventoryId, id, initialStock, 0, now, now)
        return listing
    }

    fun updateListing(
        id: Int,
        price: Double?,
        status: ListingStatus?,
    ): Listing? {
        val existing = listings[id] ?: return null
        val updated =
            existing.copy(
                price = price ?: existing.price,
                status = (status ?: existing.status),
                updatedAt = TimestampUtil.now(),
            )
        listings[id] = updated
        return updated
    }

    fun getListingById(id: Int): Listing? = listings[id]

    fun getAllPublishedListings(): Listings = listings.values.filter { it.status == ListingStatus.PUBLISHED }.sortedBy { it.id }

    fun deleteListing(id: Int): Boolean {
        // Also remove associated inventory
        inventory.remove(id)
        return listings.remove(id) != null
    }

    fun hasActiveOrders(listingId: Int): Boolean {
        // TODO: Implement when orders are added
        // For now, return false to allow deletion
        return false
    }

    fun getInventoryByListingId(listingId: Int): Inventory? = inventory[listingId]

    fun updateInventory(
        listingId: Int,
        totalQuantity: Int?,
        reservedQuantity: Int?,
    ): Inventory? {
        val existing = inventory[listingId] ?: return null
        val updated =
            existing.copy(
                totalQuantity = totalQuantity ?: existing.totalQuantity,
                reservedQuantity = reservedQuantity ?: existing.reservedQuantity,
                updatedAt = TimestampUtil.now(),
            )
        inventory[listingId] = updated
        return updated
    }
}
