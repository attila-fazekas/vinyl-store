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

import io.github.attilafazekas.vinylstore.models.LoginRequest
import io.github.attilafazekas.vinylstore.models.RegisterRequest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class PasswordToStringTest {
    @Test
    fun `Password toString does not leak the plaintext value`() {
        val password = Password("supersecret")
        password.toString() shouldNotContain "supersecret"
    }

    @Test
    fun `Password toString returns masked representation`() {
        Password("anyvalue").toString() shouldBe "Password(***)"
    }

    @Test
    fun `RegisterRequest toString does not leak the plaintext password`() {
        val request = RegisterRequest(Email("user@example.com"), Password("supersecret"))
        request.toString() shouldNotContain "supersecret"
    }

    @Test
    fun `LoginRequest toString does not leak the plaintext password`() {
        val request = LoginRequest(Email("user@example.com"), Password("supersecret"))
        request.toString() shouldNotContain "supersecret"
    }
}
