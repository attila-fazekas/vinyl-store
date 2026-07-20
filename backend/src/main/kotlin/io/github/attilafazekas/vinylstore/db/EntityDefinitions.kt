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

package io.github.attilafazekas.vinylstore.db

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
import org.komapper.annotation.KomapperEntityDef
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperIgnore
import org.komapper.annotation.KomapperTable

@KomapperEntityDef(User::class)
@KomapperTable(name = "users")
data class UserDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Address::class)
@KomapperTable(name = "addresses")
data class AddressDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Artist::class)
@KomapperTable(name = "artists")
data class ArtistDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Genre::class)
@KomapperTable(name = "genres")
data class GenreDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Label::class)
@KomapperTable(name = "labels")
data class LabelDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Vinyl::class)
@KomapperTable(name = "vinyls")
data class VinylDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Listing::class)
@KomapperTable(name = "listings")
data class ListingDef(
    @KomapperId val id: Nothing,
)

@KomapperEntityDef(Inventory::class)
@KomapperTable(name = "inventory")
data class InventoryDef(
    @KomapperId val id: Nothing,
    @KomapperIgnore val availableQuantity: Nothing,
)

@KomapperEntityDef(VinylArtist::class)
@KomapperTable(name = "vinyl_artists")
data class VinylArtistDef(
    @KomapperId val vinylId: Nothing,
    @KomapperId val artistId: Nothing,
)

@KomapperEntityDef(VinylGenre::class)
@KomapperTable(name = "vinyl_genres")
data class VinylGenreDef(
    @KomapperId val vinylId: Nothing,
    @KomapperId val genreId: Nothing,
)
