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
import io.github.attilafazekas.vinylstore.Genres
import io.github.attilafazekas.vinylstore.Password
import io.github.attilafazekas.vinylstore.enums.AddressType
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @Description("User's email address. Must be a valid email format.")
    val email: Email,
    @Description("User's password. Must be at least 8 characters long.")
    val password: Password,
)

@Serializable
data class LoginRequest(
    @Description("User's email address for authentication.")
    val email: Email,
    @Description("User's password for authentication.")
    val password: Password,
)

@Serializable
data class CreateListingRequest(
    @Description("The listing price. Must be a positive number.")
    val price: Double,
    @Description("The currency code for the price. Defaults to 'EUR'.")
    val currency: String = "EUR",
    @Description("The initial stock quantity. Must be a positive integer.")
    val initialStock: Int,
)

@Serializable
data class UpdateListingRequest(
    @Description("Optional. The new price for the listing. If omitted, price remains unchanged.")
    val price: Double? = null,
    @Description("Optional. The new status for the listing. If omitted, status remains unchanged.")
    val status: ListingStatus? = null,
)

@Serializable
data class UpdateVinylRequest(
    @Description("Optional. The new title for the vinyl. If omitted, title remains unchanged.")
    val title: String? = null,
    @Description("Optional. The new artist ID. If omitted, artist remains unchanged.")
    val artistId: Int? = null,
    @Description("Optional. The new label ID. If omitted, label remains unchanged.")
    val labelId: Int? = null,
    @Description("Optional. The new release year. If omitted, year remains unchanged.")
    val year: Int? = null,
    @Description("Optional. The new media condition rating. If omitted, media condition remains unchanged.")
    val conditionMedia: String? = null,
    @Description("Optional. The new sleeve condition rating. If omitted, sleeve condition remains unchanged.")
    val conditionSleeve: String? = null,
    @Description("Optional. The new list of genre IDs. If omitted, genres remain unchanged. If provided, replaces all existing genres.")
    val genreIds: List<Int>? = null,
)

@Serializable
data class UpdateArtistRequest(
    @Description("The new name for the artist.")
    val name: String,
)

@Serializable
data class UpdateLabelRequest(
    @Description("The new name for the label.")
    val name: String,
)

@Serializable
data class UpdateGenreRequest(
    @Description("The new name for the genre.")
    val name: String,
)

@Serializable
data class CreateVinylRequest(
    @Description("The title of the vinyl record.")
    val title: String,
    @Description("The ID of the artist who created this vinyl.")
    val artistId: Int,
    @Description("The ID of the record label that released this vinyl.")
    val labelId: Int,
    @Description("The genre ID to associate with this vinyl.")
    val genreId: Int,
    @Description("The year the vinyl was released.")
    val year: Int,
    @Description("The condition rating of the vinyl media (e.g., 'M', 'VG+', 'VG').")
    val conditionMedia: String,
    @Description("The condition rating of the vinyl sleeve (e.g., 'M', 'VG+', 'VG').")
    val conditionSleeve: String,
)

@Serializable
data class CreateArtistRequest(
    @Description("The name of the artist to create.")
    val name: String,
)

@Serializable
data class CreateGenreRequest(
    @Description("The name of the genre to create.")
    val name: String,
)

@Serializable
data class CreateLabelRequest(
    @Description("The name of the record label to create.")
    val name: String,
)

@Serializable
data class CreateAddressRequest(
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
    @Description("Whether this should be the default address for this type. Defaults to false.")
    val isDefault: Boolean = false,
)

@Serializable
data class UpdateAddressRequest(
    @Description("Optional. The new address type. If omitted, type remains unchanged.")
    val type: AddressType? = null,
    @Description("Optional. The new full name. If omitted, name remains unchanged.")
    val fullName: String? = null,
    @Description("Optional. The new street address. If omitted, street remains unchanged.")
    val street: String? = null,
    @Description("Optional. The new city. If omitted, city remains unchanged.")
    val city: String? = null,
    @Description("Optional. The new postal code. If omitted, postal code remains unchanged.")
    val postalCode: String? = null,
    @Description("Optional. The new country. If omitted, country remains unchanged.")
    val country: String? = null,
    @Description("Optional. Whether this should be the default address. If omitted, default status remains unchanged.")
    val isDefault: Boolean? = null,
)

@Serializable
data class UpdateInventoryRequest(
    @Description(
        "Optional. The new total quantity in stock. If omitted, total quantity remains unchanged. Cannot be less than current reserved quantity.",
    )
    val totalQuantity: Int? = null,
    @Description("Optional. The new reserved quantity. If omitted, reserved quantity remains unchanged. Cannot exceed total quantity.")
    val reservedQuantity: Int? = null,
)

@Serializable
data class CreateUserRequest(
    @Description("User's email address. Must be a valid email format.")
    val email: Email,
    @Description("User's password. Must be at least 8 characters long.")
    val password: Password,
    @Description("User's role in the system (CUSTOMER, STAFF, or ADMIN). Defaults to CUSTOMER.")
    val role: Role = Role.CUSTOMER,
)

@Serializable
data class UpdateUserRequest(
    @Description("Optional. The new role for the user. If omitted, role remains unchanged.")
    val role: Role? = null,
    @Description("Optional. The new active status for the user. If omitted, status remains unchanged.")
    val isActive: Boolean? = null,
)
