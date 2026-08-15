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

package io.github.attilafazekas.paymentservice.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequest(
    @Description("Reference to the order this payment is for, supplied by the calling system.")
    val orderReference: String,
    @Description("The amount to charge, expressed in the smallest currency unit (e.g., cents for EUR).")
    val amountCents: Int,
    @Description("The ISO 4217 currency code for the amount.")
    val currency: String,
    @Description("Opaque token identifying the payment method to charge (e.g., 'tok_visa').")
    val paymentMethod: String,
    @Description("Idempotency key for this charge. Retrying a charge with the same key must not result in a double charge.")
    val idempotencyKey: String,
)
