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

import org.komapper.r2dbc.R2dbcDatabase

object DatabaseFactory {
    fun create(): R2dbcDatabase {
        val host = System.getenv("POSTGRES_HOST") ?: "localhost"
        val port = System.getenv("POSTGRES_PORT") ?: "5432"
        val database = System.getenv("POSTGRES_DB") ?: "vinylstore"
        val user = System.getenv("POSTGRES_USER") ?: "vinylstore"
        val password = System.getenv("POSTGRES_PASSWORD") ?: "vinylstore"
        val url = "r2dbc:postgresql://$user:$password@$host:$port/$database"
        return R2dbcDatabase(url)
    }
}
