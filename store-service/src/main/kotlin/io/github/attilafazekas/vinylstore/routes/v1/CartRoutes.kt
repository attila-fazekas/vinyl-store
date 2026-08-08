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
import io.github.attilafazekas.vinylstore.NOT_FOUND
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VALIDATION_ERROR
import io.github.attilafazekas.vinylstore.VinylStoreRepository
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.documentation.validationErrorExample
import io.github.attilafazekas.vinylstore.enums.ListingStatus
import io.github.attilafazekas.vinylstore.models.AddCartItemRequest
import io.github.attilafazekas.vinylstore.models.CartItem
import io.github.attilafazekas.vinylstore.models.CartItemResponse
import io.github.attilafazekas.vinylstore.models.CartResponse
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Listing
import io.github.attilafazekas.vinylstore.models.UpdateCartItemRequest
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.uuid.Uuid

fun Route.cartRoutes(store: VinylStoreRepository) {
    authenticate(AUTH_JWT) {
        route("$V1/cart") {
            get(listCartDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                call.respond(buildCartResponse(store, principal.userId))
            }

            post("/items", addCartItemDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val request = call.receive<AddCartItemRequest>()

                if (request.quantity < 1) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(VALIDATION_ERROR, "Quantity must be at least 1"))
                    return@post
                }

                val listing = store.getListingById(request.listingId)
                if (listing == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                    return@post
                }
                if (listing.status != ListingStatus.Published) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Listing is not available for purchase"))
                    return@post
                }

                val cartItem = store.upsertCartItem(principal.userId, request.listingId, request.quantity)
                val response = buildCartItemResponse(store, cartItem)
                if (response == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                } else {
                    call.respond(HttpStatusCode.Created, response)
                }
            }

            put("/items/{listingId}", updateCartItemDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val listingId = call.parameters["listingId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (listingId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@put
                }

                val request = call.receive<UpdateCartItemRequest>()
                if (request.quantity < 1) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(VALIDATION_ERROR, "Quantity must be at least 1"))
                    return@put
                }

                val cartItem = store.setCartItemQuantity(principal.userId, listingId, request.quantity)
                if (cartItem == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Cart item not found"))
                    return@put
                }

                val response = buildCartItemResponse(store, cartItem)
                if (response == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Listing not found"))
                } else {
                    call.respond(response)
                }
            }

            delete("/items/{listingId}", removeCartItemDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val listingId = call.parameters["listingId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (listingId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid listing ID"))
                    return@delete
                }

                if (store.deleteCartItem(principal.userId, listingId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Cart item not found"))
                }
            }

            delete(clearCartDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                store.clearCart(principal.userId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun buildCartResponse(
    store: VinylStoreRepository,
    userId: Uuid,
): CartResponse {
    val items = store.getCartItems(userId).mapNotNull { buildCartItemResponse(store, it) }
    return CartResponse(items = items, total = items.size, currency = items.firstOrNull()?.listing?.currency)
}

private suspend fun buildCartItemResponse(
    store: VinylStoreRepository,
    cartItem: CartItem,
): CartItemResponse? {
    val listing = store.getListingById(cartItem.listingId) ?: return null
    val inventory = store.getInventoryByListingId(cartItem.listingId) ?: return null
    return CartItemResponse(
        listing = listing,
        quantity = cartItem.quantity,
        availableQuantity = inventory.availableQuantity,
        subtotal = listing.price * cartItem.quantity,
        createdAt = cartItem.createdAt,
        updatedAt = cartItem.updatedAt,
    )
}

private fun listCartDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getCart"
        summary = "Get My Cart"
        description =
            """
            Retrieve the currently authenticated user's shopping cart.

            Each line item includes:
            - Full listing details (price, currency, status)
            - Quantity requested
            - Current available quantity for the listing (total minus reserved)
            - Line subtotal (listing price multiplied by quantity)

            Adding items to the cart does not reserve inventory. Availability is only checked and
            reserved when the cart is converted into an order.
            """.trimIndent()
        tags = listOf("cart")
        response {
            code(HttpStatusCode.OK) {
                body<CartResponse> {
                    example("Cart with items") {
                        value =
                            CartResponse(
                                items =
                                    listOf(
                                        CartItemResponse(
                                            listing =
                                                Listing(
                                                    id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                                                    vinylId = Uuid.parse("550e8400-e29b-41d4-a716-446655440001"),
                                                    status = ListingStatus.Published,
                                                    price = 34.99,
                                                    currency = "EUR",
                                                    createdAt = TimestampUtil.now(),
                                                    updatedAt = TimestampUtil.now(),
                                                ),
                                            quantity = 2,
                                            availableQuantity = 8,
                                            subtotal = 69.98,
                                            createdAt = TimestampUtil.now(),
                                            updatedAt = TimestampUtil.now(),
                                        ),
                                    ),
                                total = 1,
                                currency = "EUR",
                            )
                    }
                    example("Empty cart") {
                        value = CartResponse(items = emptyList(), total = 0, currency = null)
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun addCartItemDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "addCartItem"
        summary = "Add Item To Cart"
        description =
            """
            Add a listing to the currently authenticated user's cart, or update the quantity if the
            listing is already in the cart.

            **Validation Rules:**
            - The listing must exist and have status Published
            - Quantity must be at least 1

            Adding to the cart does not reserve inventory; stock is only checked and reserved when
            the cart is converted into an order.
            """.trimIndent()
        tags = listOf("cart")
        request {
            body<AddCartItemRequest> {
                example("Add item") {
                    value =
                        AddCartItemRequest(
                            listingId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                            quantity = 2,
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<CartItemResponse> {
                    example("Item added") {
                        value =
                            CartItemResponse(
                                listing =
                                    Listing(
                                        id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                                        vinylId = Uuid.parse("550e8400-e29b-41d4-a716-446655440001"),
                                        status = ListingStatus.Published,
                                        price = 34.99,
                                        currency = "EUR",
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                                quantity = 2,
                                availableQuantity = 8,
                                subtotal = 69.98,
                                createdAt = TimestampUtil.now(),
                                updatedAt = TimestampUtil.now(),
                            )
                    }
                }
            }
            badRequestExample("Listing is not available for purchase")
            validationErrorExample("Quantity must be at least 1")
            notAuthenticatedExample()
            notFoundExample("Listing not found")
        }
    }

private fun updateCartItemDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "updateCartItem"
        summary = "Update Cart Item Quantity"
        description =
            """
            Set the quantity of a listing already in the currently authenticated user's cart.

            **Validation Rules:**
            - Quantity must be at least 1
            - The listing must already be present in the cart

            Use DELETE to remove an item from the cart entirely.
            """.trimIndent()
        tags = listOf("cart")
        request {
            pathParameter<Uuid>("listingId") {
                description = "Listing UUID"
                example("Cart item") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
            body<UpdateCartItemRequest> {
                example("Update quantity") {
                    value = UpdateCartItemRequest(quantity = 3)
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<CartItemResponse> {
                    example("Updated cart item") {
                        value =
                            CartItemResponse(
                                listing =
                                    Listing(
                                        id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                                        vinylId = Uuid.parse("550e8400-e29b-41d4-a716-446655440001"),
                                        status = ListingStatus.Published,
                                        price = 34.99,
                                        currency = "EUR",
                                        createdAt = TimestampUtil.now(),
                                        updatedAt = TimestampUtil.now(),
                                    ),
                                quantity = 3,
                                availableQuantity = 8,
                                subtotal = 104.97,
                                createdAt = TimestampUtil.now(),
                                updatedAt = TimestampUtil.now(),
                            )
                    }
                }
            }
            badRequestExample("Invalid listing UUID")
            validationErrorExample("Quantity must be at least 1")
            notAuthenticatedExample()
            notFoundExample("Cart item not found")
        }
    }

private fun removeCartItemDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "removeCartItem"
        summary = "Remove Cart Item"
        description =
            """
            Remove a single listing from the currently authenticated user's cart.
            """.trimIndent()
        tags = listOf("cart")
        request {
            pathParameter<Uuid>("listingId") {
                description = "Listing UUID"
                example("Cart item") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            code(HttpStatusCode.NoContent) {
                description = "Cart item removed successfully"
            }
            badRequestExample("Invalid listing UUID")
            notAuthenticatedExample()
            notFoundExample("Cart item not found")
        }
    }

private fun clearCartDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "clearCart"
        summary = "Clear Cart"
        description =
            """
            Remove all items from the currently authenticated user's cart.
            """.trimIndent()
        tags = listOf("cart")
        response {
            code(HttpStatusCode.NoContent) {
                description = "Cart cleared successfully"
            }
            notAuthenticatedExample()
        }
    }
