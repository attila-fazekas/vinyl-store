# Vinyl Store API

> ⚠️ **IMPORTANT: Testing Server Only** ⚠️
> 
> This server is designed **exclusively for API testing purposes** and should **NOT** be used in production environments.
> - Data is stored in **PostgreSQL**, but the schema is auto-created on startup (no versioned migrations) and default credentials are hardcoded
> - No security hardening for production workloads
> - Suitable for development, testing, and API learning purposes

## Overview

A comprehensive REST API server for managing a vinyl record store, featuring catalog management, inventory tracking, user authentication, and listings. Built with Kotlin and Ktor, this API provides a realistic testing environment for developers and testers.

The project consists of two services:

- **store-service** — the main Vinyl Store API described below
- **payment-service** — a contract-only payment processing API modeling how store-service integrates with an external payment provider (see [Payment Service](#payment-service))

## Key Features

- **JWT-based Authentication** with role-based access control (CUSTOMER, STAFF, ADMIN)
- **Catalog Management** for artists, labels, genres, and vinyl records
- **Listings & Inventory** with real-time stock tracking
- **User Management** with registration, authentication, and address management
- **Advanced Filtering** and search capabilities across all resources
- **API Versioning** with both v1 (standard) and v2 (enhanced with nested entities) endpoints
- **OpenAPI/Swagger Documentation** at [http://localhost:8080/swagger](http://localhost:8080/swagger)
- **Optional Auto-Reset** mechanism (can reset to bootstrap state every hour when enabled)

## Technology Stack

- **Language**: Kotlin
- **Framework**: Ktor (Netty server)
- **Serialization**: kotlinx.serialization
- **Authentication**: JWT (JSON Web Tokens)
- **API Documentation**: OpenAPI 3.0 with Swagger UI
- **Build Tool**: Gradle with Kotlin DSL
- **Data Storage**: PostgreSQL via Komapper (R2DBC), schema auto-created on startup (no versioned migrations)

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle (included via wrapper)
- A running PostgreSQL instance (see [Running with Docker Compose](#running-with-docker-compose) below for the easiest way to get one)

### Running with Docker Compose

The simplest way to run the full stack (API + PostgreSQL) is:

```
docker compose up --build
```

This starts a `postgres` service, the `store-service` service, and the `payment-service` together; the API will be available at [http://localhost:8080](http://localhost:8080) and the payment service at [http://localhost:9090](http://localhost:9090). Data persists in a named Docker volume across `store-service` restarts. To wipe all data (including the volume), run `docker compose down -v`.

### Running the Server Locally

To run the server directly with Gradle, you need a reachable PostgreSQL instance. You can start just the database with `docker compose up postgres`, or point the server at your own instance via environment variables (defaults shown):

- `POSTGRES_HOST` (default: `localhost`)
- `POSTGRES_PORT` (default: `5432`)
- `POSTGRES_DB` (default: `vinylstore`)
- `POSTGRES_USER` (default: `vinylstore`)
- `POSTGRES_PASSWORD` (default: `vinylstore`)

Then run `./gradlew :store-service:run` command in Terminal to start the server.

### Enabling Auto-Reset
To enable auto-reset, pass `--auto-reset` argument to the server: `./gradlew :store-service:run --args="--auto-reset"`

## Payment Service

`payment-service` is a contract-only API that models how store-service integrates with an external payment provider. It is not implemented yet — every endpoint documents its intended request and response shape, but handlers currently respond with `501 Not Implemented`. The published contract is intended to be stubbed with WireMock by calling services (such as `store-service`) during testing.

- **Endpoints**: `POST /payments` (charge a payment), `GET /payments/{paymentId}` (retrieve a payment), `GET /health` (health check)
- **OpenAPI/Swagger Documentation** at [http://localhost:9090/swagger](http://localhost:9090/swagger)

Run it directly with `./gradlew :payment-service:run`, or via `docker compose up --build`. The `store-service` service points at it through the `PAYMENT_SERVICE_URL` environment variable (default: `http://payment-service:9090` in Docker Compose).