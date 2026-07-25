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
import io.github.attilafazekas.vinylstore.OrderCreationResult
import io.github.attilafazekas.vinylstore.SERVICE_UNAVAILABLE
import io.github.attilafazekas.vinylstore.TimestampUtil
import io.github.attilafazekas.vinylstore.V1
import io.github.attilafazekas.vinylstore.VinylStoreRepository
import io.github.attilafazekas.vinylstore.documentation.badRequestExample
import io.github.attilafazekas.vinylstore.documentation.conflictExample
import io.github.attilafazekas.vinylstore.documentation.notAuthenticatedExample
import io.github.attilafazekas.vinylstore.documentation.notFoundExample
import io.github.attilafazekas.vinylstore.documentation.serviceUnavailableExample
import io.github.attilafazekas.vinylstore.enums.OrderStatus
import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.CreateOrderRequest
import io.github.attilafazekas.vinylstore.models.ErrorResponse
import io.github.attilafazekas.vinylstore.models.Order
import io.github.attilafazekas.vinylstore.models.OrderItem
import io.github.attilafazekas.vinylstore.models.OrderResponse
import io.github.attilafazekas.vinylstore.models.OrdersResponse
import io.github.attilafazekas.vinylstore.models.PayOrderRequest
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.github.attilafazekas.vinylstore.payments.PaymentChargeOutcome
import io.github.attilafazekas.vinylstore.payments.PaymentChargeRequest
import io.github.attilafazekas.vinylstore.payments.PaymentClient
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

fun Route.orderRoutes(
    store: VinylStoreRepository,
    paymentClient: PaymentClient,
) {
    authenticate(AUTH_JWT) {
        route("$V1/orders") {
            post(createOrderDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val request = call.receive<CreateOrderRequest>()

                val address = store.getAddressById(request.shippingAddressId)
                if (address == null || address.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Address not found"))
                    return@post
                }

                val cartItems = store.getCartItems(principal.userId)
                when (val result = store.createOrder(principal.userId, address, cartItems)) {
                    is OrderCreationResult.Success -> {
                        call.respond(HttpStatusCode.Created, buildOrderResponse(store, result.order))
                    }

                    is OrderCreationResult.InsufficientStock -> {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse(CONFLICT, "Insufficient stock for listing ${result.listingId}"),
                        )
                    }

                    OrderCreationResult.EmptyCart -> {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Cart is empty"))
                    }

                    OrderCreationResult.MixedCurrency -> {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Cart items must share the same currency"))
                    }

                    OrderCreationResult.ListingUnavailable -> {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "One or more listings are no longer available"))
                    }
                }
            }

            post("/{id}/pay", payOrderDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid order ID"))
                    return@post
                }

                val request = call.receive<PayOrderRequest>()

                val order = store.getOrderById(id)
                if (order == null || order.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Order not found"))
                    return@post
                }
                if (order.status != OrderStatus.Pending) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(CONFLICT, "Order is not pending payment"))
                    return@post
                }

                val centsPerUnit = 100
                val chargeRequest =
                    PaymentChargeRequest(
                        orderReference = order.id.toString(),
                        amountCents = (order.totalAmount * centsPerUnit).roundToInt(),
                        currency = order.currency,
                        paymentMethod = request.paymentMethod,
                        idempotencyKey = order.id.toString(),
                    )

                when (paymentClient.charge(chargeRequest)) {
                    is PaymentChargeOutcome.Approved -> {
                        respondOrderTransition(call, store, store.markOrderPaid(id))
                    }

                    is PaymentChargeOutcome.Declined -> {
                        respondOrderTransition(call, store, store.markOrderFailed(id))
                    }

                    is PaymentChargeOutcome.Unavailable -> {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse(SERVICE_UNAVAILABLE, "Payment service is unavailable"),
                        )
                    }
                }
            }

            post("/{id}/cancel", cancelOrderDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid order ID"))
                    return@post
                }

                val order = store.getOrderById(id)
                if (order == null || order.userId != principal.userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Order not found"))
                    return@post
                }
                if (order.status != OrderStatus.Pending) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(CONFLICT, "Order is not pending"))
                    return@post
                }

                respondOrderTransition(call, store, store.cancelOrder(id))
            }

            get(listOrdersDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!
                val orders = if (principal.isStaffOrAdmin()) store.getAllOrders() else store.getOrdersByUserId(principal.userId)
                val responses = orders.map { buildOrderResponse(store, it) }
                call.respond(OrdersResponse(responses, responses.size))
            }

            get("/{id}", getOrderDocumentation()) {
                val principal = call.principal<UserPrincipal>()!!

                val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(BAD_REQUEST, "Invalid order ID"))
                    return@get
                }

                val order = store.getOrderById(id)
                if (order == null || (order.userId != principal.userId && !principal.isStaffOrAdmin())) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(NOT_FOUND, "Order not found"))
                    return@get
                }

                call.respond(buildOrderResponse(store, order))
            }
        }
    }
}

private fun UserPrincipal.isStaffOrAdmin(): Boolean = Role.valueOf(role) in setOf(Role.Staff, Role.Admin)

private suspend fun buildOrderResponse(
    store: VinylStoreRepository,
    order: Order,
): OrderResponse = OrderResponse(order = order, items = store.getOrderItems(order.id))

private suspend fun respondOrderTransition(
    call: ApplicationCall,
    store: VinylStoreRepository,
    order: Order?,
) {
    if (order == null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse(CONFLICT, "Order is not pending"))
    } else {
        call.respond(buildOrderResponse(store, order))
    }
}

private fun exampleOrder(status: OrderStatus): Order =
    Order(
        id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
        userId = Uuid.parse("550e8400-e29b-41d4-a716-446655440001"),
        status = status,
        totalAmount = 69.98,
        currency = "EUR",
        shippingFullName = "John Doe",
        shippingStreet = "123 Main St",
        shippingCity = "New York",
        shippingPostalCode = "10001",
        shippingCountry = "USA",
        addressId = Uuid.parse("550e8400-e29b-41d4-a716-446655440002"),
        createdAt = TimestampUtil.now(),
        updatedAt = TimestampUtil.now(),
    )

private fun exampleOrderItems(orderId: Uuid): List<OrderItem> =
    listOf(
        OrderItem(
            id = Uuid.parse("550e8400-e29b-41d4-a716-446655440003"),
            orderId = orderId,
            listingId = Uuid.parse("550e8400-e29b-41d4-a716-446655440004"),
            title = "Kind of Blue",
            unitPrice = 34.99,
            currency = "EUR",
            quantity = 2,
        ),
    )

private fun createOrderDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "createOrder"
        summary = "Create Order"
        description =
            """
            Convert the currently authenticated user's cart into a `Pending` order, shipping to one
            of their own addresses.

            **Validation Rules:**
            - `shippingAddressId` must belong to the caller
            - The cart must not be empty
            - All cart line items must share the same currency
            - Every listing must still be `Published`
            - Sufficient stock must be available for every line item

            On success, inventory is reserved for each line item, the cart is cleared, and prices are
            snapshotted onto the order. Call `POST /orders/{id}/pay` next to charge the order.
            """.trimIndent()
        tags = listOf("orders")
        request {
            body<CreateOrderRequest> {
                example("Checkout") {
                    value = CreateOrderRequest(shippingAddressId = Uuid.parse("550e8400-e29b-41d4-a716-446655440002"))
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                body<OrderResponse> {
                    example("Created order") {
                        val order = exampleOrder(OrderStatus.Pending)
                        value = OrderResponse(order = order, items = exampleOrderItems(order.id))
                    }
                }
            }
            badRequestExample("Cart is empty")
            notAuthenticatedExample()
            notFoundExample("Address not found")
            conflictExample("Insufficient stock" to "Insufficient stock for listing 550e8400-e29b-41d4-a716-446655440004")
        }
    }

private fun payOrderDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "payOrder"
        summary = "Pay Order"
        description =
            """
            Charge a `Pending` order through the payment service.

            Only `Pending` orders can be paid; retrying on an already-`Paid`, `Failed`, or `Cancelled`
            order returns `409` (double-charge guard) without contacting the payment service.

            Outcomes:
            - The payment service approves the charge: the order transitions to `Paid` and inventory is
              converted from reserved to sold.
            - The payment service declines the charge: the order transitions to `Failed` and its
              reservation is released.
            - The payment service is unreachable, times out, or errors: the order stays `Pending` and
              this endpoint returns `503`. Retry once the payment service recovers.
            """.trimIndent()
        tags = listOf("orders")
        request {
            pathParameter<Uuid>("id") {
                description = "Order UUID"
                example("Order") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
            body<PayOrderRequest> {
                example("Pay with card token") {
                    value = PayOrderRequest(paymentMethod = "tok_visa")
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<OrderResponse> {
                    example("Paid order") {
                        val order = exampleOrder(OrderStatus.Paid)
                        value = OrderResponse(order = order, items = exampleOrderItems(order.id))
                    }
                    example("Failed order") {
                        val order = exampleOrder(OrderStatus.Failed)
                        value = OrderResponse(order = order, items = exampleOrderItems(order.id))
                    }
                }
            }
            badRequestExample("Invalid order ID")
            notAuthenticatedExample()
            notFoundExample("Order not found")
            conflictExample("Not pending" to "Order is not pending payment")
            serviceUnavailableExample("Payment service is unavailable")
        }
    }

private fun cancelOrderDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "cancelOrder"
        summary = "Cancel Order"
        description =
            """
            Cancel a `Pending` order, releasing its inventory reservation.

            Only `Pending` orders can be cancelled; a `Paid`, `Failed`, or already-`Cancelled` order
            returns `409`.
            """.trimIndent()
        tags = listOf("orders")
        request {
            pathParameter<Uuid>("id") {
                description = "Order UUID"
                example("Order") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<OrderResponse> {
                    example("Cancelled order") {
                        val order = exampleOrder(OrderStatus.Cancelled)
                        value = OrderResponse(order = order, items = exampleOrderItems(order.id))
                    }
                }
            }
            badRequestExample("Invalid order ID")
            notAuthenticatedExample()
            notFoundExample("Order not found")
            conflictExample("Not pending" to "Order is not pending")
        }
    }

private fun listOrdersDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "listOrders"
        summary = "List Orders"
        description =
            """
            Retrieve orders, each with its line items.

            - Customers see only their own orders.
            - Staff and Admin see every order in the system.
            """.trimIndent()
        tags = listOf("orders")
        response {
            code(HttpStatusCode.OK) {
                body<OrdersResponse> {
                    example("Orders") {
                        val order = exampleOrder(OrderStatus.Paid)
                        value =
                            OrdersResponse(
                                orders = listOf(OrderResponse(order = order, items = exampleOrderItems(order.id))),
                                total = 1,
                            )
                    }
                }
            }
            notAuthenticatedExample()
        }
    }

private fun getOrderDocumentation(): RouteConfig.() -> Unit =
    {
        operationId = "getOrder"
        summary = "Get Order"
        description =
            """
            Retrieve a single order with its line items.

            Access control:
            - Customers may only read their own orders
            - Staff and Admin may read any order
            - Returns `404` for a non-existent order, or another customer's order (to avoid leaking
              existence)
            """.trimIndent()
        tags = listOf("orders")
        request {
            pathParameter<Uuid>("id") {
                description = "Order UUID"
                example("Order") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                body<OrderResponse> {
                    example("Order details") {
                        val order = exampleOrder(OrderStatus.Pending)
                        value = OrderResponse(order = order, items = exampleOrderItems(order.id))
                    }
                }
            }
            badRequestExample("Invalid order ID")
            notAuthenticatedExample()
            notFoundExample("Order not found")
        }
    }
