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

import io.github.attilafazekas.vinylstore.Addresses
import io.github.attilafazekas.vinylstore.Artists
import io.github.attilafazekas.vinylstore.Email
import io.github.attilafazekas.vinylstore.Genres
import io.github.attilafazekas.vinylstore.Inventories
import io.github.attilafazekas.vinylstore.Labels
import io.github.attilafazekas.vinylstore.ListingDetailResponses
import io.github.attilafazekas.vinylstore.Vinyls
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    @Description("JWT authentication token for subsequent API requests.")
    val token: String,
    @Description("Authenticated user's profile information.")
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    @Description("Unique identifier for the user.")
    val id: Int,
    @Description("User's email address.")
    val email: Email,
    @Description("User's role in the system (CUSTOMER, STAFF, or ADMIN).")
    val role: Role,
    @Description("Whether the user account is active and can authenticate.")
    val isActive: Boolean,
)

@Serializable
data class ErrorResponse(
    @Description("The error type or category.")
    val error: String,
    @Description("A detailed error message describing what went wrong.")
    val message: String,
)

@Serializable
data class HealthResponse(
    @Description("The health status of the API (typically 'ok').")
    val status: String,
    @Description("Uptime since the server started (format: HH:MM:SS).")
    val uptime: String,
    @Description("Time until the next automatic data reset (format: HH:MM:SS). Only present if auto-reset is enabled.")
    val nextResetIn: String? = null,
)

@Serializable
data class ListingsResponse(
    @Description("Array of listing details including vinyl, artist, label, genres, and inventory information.")
    val listings: ListingDetailResponses,
    @Description("Total number of listings matching the query.")
    val total: Int,
)

// V2 Response models with hierarchical structure
@Serializable
data class VinylWithDetailsV2(
    @Description("Unique identifier for the vinyl record.")
    val id: Int,
    @Description("The title of the vinyl record.")
    val title: String,
    @Description("The year the vinyl was released.")
    val year: Int,
    @Description("The condition rating of the vinyl media.")
    val conditionMedia: String,
    @Description("The condition rating of the vinyl sleeve.")
    val conditionSleeve: String,
    @Description("The artists who created this vinyl, with full details embedded. Contains multiple artists for collaborations.")
    val artists: Artists,
    @Description("The record label that released this vinyl, with full details embedded.")
    val label: Label,
    @Description("The genre associated with this vinyl, with full details embedded.")
    val genre: Genre,
)

@Serializable
data class InventoryV2(
    @Description("Total quantity in stock.")
    val totalQuantity: Int,
    @Description("Quantity currently reserved for pending orders.")
    val reservedQuantity: Int,
    @Description("Quantity available for purchase (total minus reserved).")
    val availableQuantity: Int,
)

@Serializable
data class ListingV2Response(
    @Description("Unique identifier for the listing.")
    val id: Int,
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
    @Description("Inventory information with quantities embedded.")
    val inventory: InventoryV2,
    @Description("Vinyl details with artist, label, and genres embedded.")
    val vinyl: VinylWithDetailsV2,
)

@Serializable
data class ListingsV2Response(
    @Description("Array of listings with embedded vinyl, inventory, artist, label, and genre details.")
    val listings: List<ListingV2Response>,
    @Description("Total number of listings matching the query.")
    val total: Int,
)

@Serializable
data class ListingDetailResponse(
    @Description("The listing information including price and status.")
    val listing: Listing,
    @Description("The vinyl record details.")
    val vinyl: Vinyl,
    @Description("The artist who created the vinyl.")
    val artist: Artist,
    @Description("The genre associated with the vinyl.")
    val genre: Genre,
    @Description("The record label that released the vinyl.")
    val label: Label,
    @Description("The current inventory status for this listing.")
    val inventory: Inventory,
)

@Serializable
data class VinylsV2Response(
    @Description("Array of vinyl records with embedded artist, label, and genre details.")
    val vinyls: List<VinylWithDetailsV2>,
    @Description("Total number of vinyls matching the query.")
    val total: Int,
)

@Serializable
data class ArtistsResponse(
    @Description("Array of artists.")
    val artists: Artists,
    @Description("Total number of artists matching the query.")
    val total: Int,
)

@Serializable
data class GenresResponse(
    @Description("Array of genres.")
    val genres: Genres,
    @Description("Total number of genres matching the query.")
    val total: Int,
)

@Serializable
data class LabelsResponse(
    @Description("Array of record labels.")
    val labels: Labels,
    @Description("Total number of labels matching the query.")
    val total: Int,
)

@Serializable
data class VinylsResponse(
    @Description("Array of vinyl records.")
    val vinyls: Vinyls,
    @Description("Total number of vinyls matching the query.")
    val total: Int,
)

@Serializable
data class VinylDetailResponse(
    @Description("The vinyl record details.")
    val vinyl: Vinyl,
    @Description("The artist who created this vinyl.")
    val artist: Artist,
    @Description("The record label that released this vinyl.")
    val label: Label,
    @Description("The genre associated with this vinyl.")
    val genre: Genre,
)

@Serializable
data class AddressesResponse(
    @Description("Array of user addresses.")
    val addresses: Addresses,
    @Description("Total number of addresses matching the query.")
    val total: Int,
)

@Serializable
data class MessageResponse(
    @Description("A message describing the result of the operation.")
    val message: String,
)

@Serializable
data class UsersResponse(
    @Description("Array of user profiles.")
    val users: List<UserResponse>,
    @Description("Total number of users matching the query.")
    val total: Int,
)

@Serializable
data class InventoryResponse(
    @Description("Array of inventory records.")
    val inventory: Inventories,
    @Description("Total number of inventory records matching the query.")
    val total: Int,
)

// V2 Inventory models with listing context
@Serializable
data class VinylContextV2(
    @Description("Unique identifier for the vinyl record.")
    val id: Int,
    @Description("The title of the vinyl record.")
    val title: String,
    @Description("The artists who created this vinyl, with basic details embedded. Contains multiple artists for collaborations.")
    val artists: Artists,
)

@Serializable
data class ListingContextV2(
    @Description("Unique identifier for the listing.")
    val id: Int,
    @Description("Current status of the listing.")
    val status: ListingStatus,
    @Description("The listing price.")
    val price: Double,
    @Description("The currency code for the price.")
    val currency: String,
    @Description("Basic vinyl information with artist details embedded.")
    val vinyl: VinylContextV2,
)

@Serializable
data class InventoryWithListingV2Response(
    @Description("Unique identifier for the inventory record.")
    val id: Int,
    @Description("Total quantity in stock.")
    val totalQuantity: Int,
    @Description("Quantity currently reserved for pending orders.")
    val reservedQuantity: Int,
    @Description("Quantity available for purchase.")
    val availableQuantity: Int,
    @Description("Associated listing information with vinyl and artist details embedded.")
    val listing: ListingContextV2,
)

@Serializable
data class InventoriesV2Response(
    @Description("Array of inventory records with embedded listing and vinyl details.")
    val inventory: List<InventoryWithListingV2Response>,
    @Description("Total number of inventory records matching the query.")
    val total: Int,
)

// V2 User models with embedded addresses and stats
@Serializable
data class UserStatsV2(
    @Description("Total number of orders placed by the user.")
    val totalOrders: Int,
    @Description("Timestamp when the account was created in ISO 8601 format with UTC timezone (e.g., '2025-01-10T14:30:45.123Z').")
    val accountCreated: String,
)

@Serializable
data class UserV2Response(
    @Description("Unique identifier for the user.")
    val id: Int,
    @Description("User's email address.")
    val email: Email,
    @Description("User's role in the system.")
    val role: Role,
    @Description("Whether the user account is active.")
    val isActive: Boolean,
    @Description("All addresses associated with the user, embedded in the response.")
    val addresses: Addresses,
    @Description("User statistics including order count and account creation date.")
    val stats: UserStatsV2,
)
