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

package io.github.attilafazekas.vinylstore.models

import io.github.attilafazekas.vinylstore.Email
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @Description("Unique identifier for the user.")
    val id: Int,
    @Description("User's email address.")
    val email: Email,
    @Description("Hashed password for authentication.")
    val passwordHash: String,
    @Description("User's role in the system (CUSTOMER, STAFF, or ADMIN).")
    val role: Role,
    @Description("Whether the user account is active and can authenticate.")
    val isActive: Boolean,
    @Description("Timestamp when the account was created in ISO 8601 format with UTC timezone (e.g., '2025-01-10T14:30:45.123Z').")
    val createdAt: String,
)

@Serializable
data class Address(
    @Description("Unique identifier for the address.")
    val id: Int,
    @Description("ID of the user who owns this address.")
    val userId: Int,
    @Description("The type of address (SHIPPING or BILLING).")
    val type: AddressType,
    @Description("The full name of the recipient.")
    val fullName: String,
    @Description("The street address including house number.")
    val street: String,
    @Description("The city name.")
    val city: String,
    @Description("The postal or ZIP code.")
    val postalCode: String,
    @Description("The country name.")
    val country: String,
    @Description("Whether this is the default address for this type.")
    val isDefault: Boolean,
)

@Serializable
data class Artist(
    @Description("Unique identifier for the artist.")
    val id: Int,
    @Description("The name of the artist.")
    val name: String,
)

@Serializable
data class Genre(
    @Description("Unique identifier for the genre.")
    val id: Int,
    @Description("The name of the genre.")
    val name: String,
)

@Serializable
data class Label(
    @Description("Unique identifier for the record label.")
    val id: Int,
    @Description("The name of the record label.")
    val name: String,
)

@Serializable
data class Vinyl(
    @Description("Unique identifier for the vinyl record.")
    val id: Int,
    @Description("The title of the vinyl record.")
    val title: String,
    @Description("ID of the artist who created this vinyl.")
    val artistId: Int,
    @Description("ID of the record label that released this vinyl.")
    val labelId: Int,
    @Description("ID of the genre associated with this vinyl.")
    val genreId: Int,
    @Description("The year the vinyl was released.")
    val year: Int,
    @Description("The condition rating of the vinyl media (e.g., 'M', 'VG+', 'VG').")
    val conditionMedia: String,
    @Description("The condition rating of the vinyl sleeve (e.g., 'M', 'VG+', 'VG').")
    val conditionSleeve: String,
)

@Serializable
data class Listing(
    @Description("Unique identifier for the listing.")
    val id: Int,
    @Description("ID of the vinyl record being listed.")
    val vinylId: Int,
    @Description("Current status of the listing (DRAFT, PUBLISHED, or ARCHIVED).")
    val status: ListingStatus,
    @Description("The listing price.")
    val price: Double,
    @Description("The currency code for the price.")
    val currency: String,
    @Description("Timestamp when the listing was created in ISO 8601 format with UTC timezone (e.g., '2025-01-10T14:30:45.123Z').")
    val createdAt: String,
    @Description("Timestamp when the listing was last updated in ISO 8601 format with UTC timezone (e.g., '2025-01-10T14:30:45.123Z').")
    val updatedAt: String,
)

@Serializable
data class Inventory(
    @Description("Unique identifier for the inventory record.")
    val id: Int,
    @Description("ID of the listing this inventory belongs to.")
    val listingId: Int,
    @Description("Total quantity in stock.")
    val totalQuantity: Int,
    @Description("Quantity currently reserved for pending orders.")
    val reservedQuantity: Int,
) {
    @Description("Computed quantity available for purchase (total minus reserved).")
    val availableQuantity: Int get() = totalQuantity - reservedQuantity
}

@Serializable
data class VinylGenre(
    @Description("ID of the vinyl record.")
    val vinylId: Int,
    @Description("ID of the genre associated with the vinyl.")
    val genreId: Int,
)

@Serializable
data class VinylArtist(
    @Description("ID of the vinyl record.")
    val vinylId: Int,
    @Description("ID of the artist associated with the vinyl.")
    val artistId: Int,
)
