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

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtil {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(password: Password): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(password.value, salt, ITERATIONS, KEY_LENGTH_BITS)
        val encoder = Base64.getEncoder()
        return $$"pbkdf2$$$ITERATIONS$$${encoder.encodeToString(salt)}$$${encoder.encodeToString(derived)}"
    }

    fun verify(
        password: Password,
        hash: String,
    ): Boolean {
        val parts = hash.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[2])
        val stored = decoder.decode(parts[3])
        val computed = pbkdf2(password.value, salt, iterations, stored.size * 8)
        return MessageDigest.isEqual(stored, computed)
    }

    private fun pbkdf2(
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }
}
