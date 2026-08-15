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

package io.github.attilafazekas.paymentservice

import io.github.attilafazekas.common.TimestampUtil
import io.github.attilafazekas.common.enums.PaymentStatus
import io.github.attilafazekas.common.models.PaymentRequest
import io.github.attilafazekas.common.models.PaymentResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val DECLINED_PAYMENT_METHOD = "tok_chargeDeclined"

class PaymentRepository {
    private val paymentsById = ConcurrentHashMap<Uuid, PaymentResponse>()
    private val paymentsByIdempotencyKey = ConcurrentHashMap<String, PaymentResponse>()

    fun charge(request: PaymentRequest): PaymentResponse =
        paymentsByIdempotencyKey.computeIfAbsent(request.idempotencyKey) {
            createResponse(request).also { response -> paymentsById[response.paymentId] = response }
        }

    private fun createResponse(request: PaymentRequest): PaymentResponse {
        val declined = request.paymentMethod == DECLINED_PAYMENT_METHOD
        return PaymentResponse(
            paymentId = Uuid.random(),
            status = if (declined) PaymentStatus.Failed else PaymentStatus.Succeeded,
            orderReference = request.orderReference,
            amountCents = request.amountCents,
            currency = request.currency,
            failureReason = if (declined) "Card declined" else null,
            createdAt = TimestampUtil.now(),
        )
    }

    fun getById(paymentId: Uuid): PaymentResponse? = paymentsById[paymentId]
}
