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

import io.github.attilafazekas.vinylstore.enums.Role
import io.github.attilafazekas.vinylstore.models.UserPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

fun ApplicationCall.requireRole(vararg allowedRoles: Role) {
    val principal =
        principal<UserPrincipal>()
            ?: throw AuthException.Unauthenticated()

    if (principal.role !in allowedRoles.map { it.name }) {
        throw AuthException.InsufficientPermissions()
    }
}
