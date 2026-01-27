# Vinyl Store API

> ⚠️ **IMPORTANT: Testing Server Only** ⚠️
> 
> This server is designed **exclusively for API testing purposes** and should **NOT** be used in production environments.
> - Data is stored **in-memory only** (no persistence)
> - All data **automatically resets every hour**
> - No security hardening for production workloads
> - Suitable for development, testing, and API learning purposes

## Overview

A comprehensive REST API server for managing a vinyl record store, featuring catalog management, inventory tracking, user authentication, and listings. Built with Kotlin and Ktor, this API provides a realistic testing environment for developers and testers.

## Key Features

- 🔐 **JWT-based Authentication** with role-based access control (CUSTOMER, STAFF, ADMIN)
- 📀 **Catalog Management** for artists, labels, genres, and vinyl records
- 📦 **Listings & Inventory** with real-time stock tracking
- 👤 **User Management** with registration, authentication, and address management
- 🔍 **Advanced Filtering** and search capabilities across all resources
- 📚 **API Versioning** with both v1 (standard) and v2 (enhanced with nested entities) endpoints
- 📖 **OpenAPI/Swagger Documentation** at `/swagger`
- ⏰ **Auto-Reset** mechanism (resets to bootstrap state every hour)

## Technology Stack

- **Language**: Kotlin
- **Framework**: Ktor (Netty server)
- **Serialization**: kotlinx.serialization
- **Authentication**: JWT (JSON Web Tokens)
- **API Documentation**: OpenAPI 3.0 with Swagger UI
- **Build Tool**: Gradle with Kotlin DSL
- **Data Storage**: In-memory (ConcurrentHashMap)

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle (included via wrapper)

### Running the Server

Run `./gradlew :backend:run` command in Terminal to start the server.