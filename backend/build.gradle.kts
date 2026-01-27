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

plugins {
    id("vinyl-store.detekt-conventions")
    id("vinyl-store.ktlint-conventions")
    id("vinyl-store.ktor-server-conventions")
    id("vinyl-store.test-conventions")
    id("io.ktor.plugin") version "3.4.0"
}

application {
    mainClass.set("io.github.attilafazekas.vinylstore.ApplicationKt")
}
