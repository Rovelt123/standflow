# Issue # "Insert issue"

## Purpose

Add support for searching customers by name through:

`GET /api/customers?name=<search-term>`

The search must be case-insensitive and may match partial names.

## Owner

Human owner: @jon  
Agent branch: `jon/142-customer-search`  
Base branch: `main`

## Allowed scope

The agent may modify:

- `src/main/java/customer/CustomerController.java`
- `src/main/java/customer/CustomerService.java`
- `src/main/java/customer/CustomerRepository.java`
- `src/test/java/customer/CustomerResourceIT.java`

The agent may create:

- `src/main/java/customer/dto/CustomerSearchResponse.java`

## Must not be changed

The agent must not modify:

- `pom.xml`
- `docker-compose.yml`
- `src/main/java/security/`
- `src/main/java/config/`
- `src/main/resources/db/migration/`
- Existing public API endpoints
- Existing JPA entities

The agent must not:

- Add dependencies.
- Modify the database schema.
- Rename existing classes or methods.
- Perform general refactoring.
- Format or rewrite unrelated files.

If such a change appears necessary, implementation must stop and the required change must be described in the ticket.

## Functional requirements

1. `GET /api/customers?name=anna` returns customers whose names contain `anna`.
2. The search must be case-insensitive.
3. A missing `name` parameter must preserve the endpoint's existing behavior.
4. A blank `name` parameter must be treated as missing.
5. If no results are found, the endpoint must return HTTP 200 with an empty JSON array.
6. The existing JSON response format must not be changed.

## Technical guardrails

- Use the existing repository and service architecture.
- Use JPQL or the existing query strategy.
- Do not introduce a new abstraction solely for this feature.
- The controller must not access the database directly.
- Reuse the existing error-handling approach.
- No change may require a database migration.

## Expected behavior

Example:

`GET /api/customers?name=ann`

`HTTP 200`

```json
[
  {
    "id": 17,
    "name": "Anna Jensen"
  },
  {
    "id": 31,
    "name": "Hanne Sørensen"
  }
]
````

```

Den kan I have stående som ren tekst i Google Docs og derefter kopiere direkte ind i fx `issue-template.md`.
```
