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

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

dependencies {
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1") {
            because("CVE-2026-54512 and CVE-2026-54513 are fixed in 2.22.1")
        }
        implementation("tools.jackson.core:jackson-databind:3.1.4") {
            because("CVE-2026-54512 and CVE-2026-54513 are fixed in 3.1.4")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        optIn.addAll(
            "kotlin.uuid.ExperimentalUuidApi",
        )
    }
}
