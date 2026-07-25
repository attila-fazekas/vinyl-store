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
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class PasswordUtilTest {
    @Test
    fun `hash and verify round-trips correctly`() {
        val password = Password("correct-horse-battery-staple")
        val hash = PasswordUtil.hash(password)
        PasswordUtil.verify(password, hash) shouldBe true
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = PasswordUtil.hash(Password("rightpassword"))
        PasswordUtil.verify(Password("wrongpassword"), hash) shouldBe false
    }

    @Test
    fun `hashing the same password twice produces different hashes (per-password salt)`() {
        val password = Password("samepassword")
        val hash1 = PasswordUtil.hash(password)
        val hash2 = PasswordUtil.hash(password)
        hash1 shouldNotBe hash2
    }

    @Test
    fun `hash output uses pbkdf2 format`() {
        val hash = PasswordUtil.hash(Password("anypassword"))
        hash.startsWith("pbkdf2$") shouldBe true
        hash.split("$").size shouldBe 4
    }

    @Test
    fun `verify returns false for legacy sha-256 hash`() {
        val legacyHash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        PasswordUtil.verify(Password("password"), legacyHash) shouldBe false
    }
}
