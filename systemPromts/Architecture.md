# Overview

Say I Do is a full-stack wedding planning application.
The application is divided into two main parts:

- frontend/ — User interface and client-side application.
- backend/ — REST API, business logic and database communication.

The frontend communicates with the backend through HTTP requests.

```text
User
  |
  v
React Frontend
  |
  | HTTP / REST
  v
Javalin Backend
  |
  v
Hibernate / JPA
  |
  v
PostgreSQL
```

# Technology stack

## Frontend

The frontend is built using:

- JavaScript
- React
- Vite
- React Router
- ESLint
- css.modules

React is responsible for building the user interface.
Vite is used as the frontend development server and build tool.
React Router handles client-side navigation.

## Backend

The backend is built using:

- Java 21
- Maven
- Javalin
- Hibernate ORM
- PostgreSQL
- Jackson
- BCrypt
- TokenSecurity
- Jakarta Mail
- SLF4J

Javalin is responsible for handling HTTP requests and exposing the application's API.
Hibernate handles persistence and communication between Java entities and the database.
PostgreSQL is used as the production relational database.
Jackson is used for serialization and deserialization between Java objects and JSON.
BCrypt is used for password hashing.
TokenSecurity is used for token-based authentication.

## Testing

The backend uses:

- JUnit 5
- H2

JUnit is used for automated backend testing.
H2 may be used as an in-memory database during tests to avoid depending on the production PostgreSQL database.
Frontend code should pass the configured ESLint validation before work is considered complete.

# Repository structure

The repository is divided into the following main areas:

```text
4.Sem-Sys/
|
├── .github/
│   └── workflows/
|
├── backend/
│   ├── src/
│   └── pom.xml
|
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.js
│   └── index.html
|
├── documents/
|
├── AGENTS.md
├── ARCHITECTURE.md
├── CODESTANDARD.md
└── README.md
```

# Frontend architecture

The frontend is responsible for:

- Rendering the user interface.
- Handling user interaction.
- Managing client-side state.
- Client-side navigation.
- Sending requests to the backend API.
- Displaying data returned by the backend.
- Performing appropriate client-side validation.

The frontend must not communicate directly with the database.

```text
React Component
      |
      v
Frontend logic
      |
      v
HTTP request
      |
      v
Backend API
```

## Components

Reusable UI should be separated into React components where appropriate.
Components should have a clear responsibility and should not contain unrelated functionality.
Pages may combine multiple components to create complete application views.

## Routing

Navigation between frontend pages is handled using React Router.
Routing changes should remain within the frontend unless a corresponding backend API change is explicitly required.

# Backend architecture

The backend is responsible for:

- Exposing HTTP endpoints.
- Validating incoming requests.
- Handling authentication and authorization.
- Executing business logic.
- Reading and modifying persisted data.
- Returning appropriate HTTP responses.

The backend should maintain separation between HTTP handling, application logic and persistence where possible.
The expected request flow is:

```text
HTTP Request
     |
     v
Controller / Route Handler
     |
     v
Business Logic
     |
     v
Persistence Layer
     |
     v
Hibernate
     |
     v
PostgreSQL
```

Changes should follow the existing backend structure rather than introducing a new architectural pattern without explicit approval.

# Persistence

Persistence is handled using Hibernate ORM.
Database entities represent persisted application data and are mapped to the relational database.
PostgreSQL is the primary database.

```text
Java Entity
     |
     v
Hibernate ORM
     |
     v
PostgreSQL
```

Entity relationships and database mappings are considered architectural concerns.
They must not be changed as part of unrelated work.
Changes to:

- Entity relationships
- Database structure
- Persistence configuration
- Primary or foreign keys

should only be made when required by the assigned issue.

# API communication

Frontend and backend communicate using HTTP and JSON.

```text
Frontend
   |
   | JSON Request
   v
Backend API
   |
   | JSON Response
   v
Frontend
```

Jackson handles JSON serialization and deserialization on the backend.
Existing API contracts should remain stable unless an issue explicitly requires them to change.
An API contract includes:

- Endpoint path
- HTTP method
- Request structure
- Response structure
- Expected status codes

Changing an API contract may require corresponding changes in both frontend and backend.

# Authentication

Authentication is handled by the backend.
Passwords must not be stored in plain text.
BCrypt is used for password hashing.
Token-based authentication is handled using the existing TokenSecurity implementation.
The frontend may store and send authentication information as required by the existing implementation, but must not perform authentication or authorization decisions that belong to the backend.
Authorization must be enforced by the backend.

# Dependency boundaries

The following dependency direction should be maintained:

```text
Frontend
   |
   v
Backend API
   |
   v
Persistence
   |
   v
Database
```

The following should not occur:

```text
Frontend ------> Database
```

Frontend components must not directly access persistence infrastructure.
Database-specific logic should remain within the backend.

# Architectural guardrails

When modifying the application:

- Follow the existing frontend and backend structure.
- Do not introduce a new framework without explicit approval.
- Do not move responsibilities between frontend and backend without reason.
- Do not modify database architecture unless required by the assigned issue.
- Do not modify authentication architecture unless explicitly required.
- Do not change existing API contracts unnecessarily.
- Prefer extending existing patterns over introducing new architectural patterns.
- Avoid unnecessary dependencies.
- Keep changes limited to the scope of the assigned issue.

# Build tools

## Frontend

The frontend uses npm and Vite.
Common commands:

```bash
npm run dev
npm run build
npm run lint
```

## Backend

The backend uses Maven.
Common commands:

```bash
mvn test
mvn package
```

The backend Maven build creates an executable JAR containing the application's dependencies.

# Architectural changes

A change is considered architectural when it significantly changes how parts of the system interact.
Examples include:

- Introducing a new framework.
- Replacing the database.
- Changing the authentication strategy.
- Changing entity relationships.
- Introducing a new application layer.
- Moving business logic between frontend and backend.
- Restructuring the API.
- Replacing major dependencies.

Architectural changes must not be made as unrelated refactoring and require explicit approval before implementation.
