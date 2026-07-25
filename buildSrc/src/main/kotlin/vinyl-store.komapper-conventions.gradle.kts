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
    id("vinyl-store.kotlin-conventions")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("org.komapper:komapper-starter-r2dbc:_")
    implementation("org.komapper:komapper-dialect-postgresql-r2dbc:_")
    ksp("org.komapper:komapper-processor:_")
}