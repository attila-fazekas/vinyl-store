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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.komapper.r2dbc.R2dbcDatabase

class VinylStoreRepositoryResetTest {
    private val repository = VinylStoreRepository(R2dbcDatabase("r2dbc:postgresql://vinylstore:vinylstore@localhost/vinylstore"))

    @Test
    fun `shouldReset returns false for a freshly constructed repository`() {
        repository.shouldReset() shouldBe false
    }

    @Test
    fun `shouldReset returns true when lastResetAt is older than RESET_INTERVAL_MS`() {
        repository.lastResetAt = System.currentTimeMillis() - VinylStoreRepository.RESET_INTERVAL_MS - 1
        repository.shouldReset() shouldBe true
    }

    @Test
    fun `shouldReset returns false after advancing lastResetAt (simulating a reset)`() {
        repository.lastResetAt = System.currentTimeMillis() - VinylStoreRepository.RESET_INTERVAL_MS - 1
        repository.lastResetAt = System.currentTimeMillis()
        repository.shouldReset() shouldBe false
    }
}
