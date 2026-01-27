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
    `kotlin-dsl`
    id("com.diffplug.spotless") version "8.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:_")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:_")
    implementation("io.kotest:io.kotest.gradle.plugin:_")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:_")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:_")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:_")
}

kotlin {
    jvmToolchain(21)
}

spotless {
    kotlin {
        target("**/*.kt")
        endWithNewline()
        trimTrailingWhitespace()
        licenseHeaderFile(file("${project.rootDir}/../spotless/copyright.kt"))
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("settings.gradle.kts")
        endWithNewline()
        trimTrailingWhitespace()
        licenseHeaderFile(file("${project.rootDir}/../spotless/copyright.kt"), "(package |@file|import |fun )|buildscript |plugins |subprojects |spotless ")
    }
}

tasks.spotlessCheck {
    dependsOn(tasks.spotlessApply)
}

configurations.all {
    resolutionStrategy {
        // workaround for https://github.com/jreleaser/jreleaser/issues/1643
        force("org.eclipse.jgit:org.eclipse.jgit:5.13.0.202109080827-r")
    }
}