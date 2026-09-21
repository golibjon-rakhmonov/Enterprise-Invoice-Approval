# Enterprise Invoice Approval

A Spring Boot service for submitting invoices and getting them approved. Employees upload an invoice
file with the vendor and amount. A small rule engine decides the clear cases on its own, the rest wait
for a manager, and every status change is written to an audit log through domain events.

Authentication is handled by Keycloak (OAuth2 / JWT), invoice files are stored in MinIO (S3-compatible),
and the code is organised as a modular monolith with Spring Modulith.

## How it works

```
 employee ──upload──► InvoiceController ──► InvoiceService ──► MinIO (file)
                                               │
                                               ├─► rule engine ──► APPROVED / REJECTED / PENDING_MANAGER
                                               │
 manager ──decision─► InvoiceController ───────┤
                                               ▼
                                   InvoiceStatusChangedEvent
                                               │  (after commit)
                                               ▼
                                   audit module ──► audit_logs
```

1. **Upload.** Any authenticated user sends the file, vendor name and amount. On their first request
   a local user record is created from the token's `email` and `name` claims. The file goes to the
   `invoices` bucket under a random prefix.
2. **Rule engine.** Every `ApprovalRule` bean runs in `@Order`. The first rule that makes a decision
   stops the chain; if none does, the invoice waits as `PENDING_MANAGER`.

   | Order | Rule | Condition | Result |
   |---|---|---|---|
   | 1 | `BlackListRule` | vendor name contains "scam" | `REJECTED` |
   | 10 | `SmallAmountRule` | amount below 500 | `APPROVED` |

   A new rule is one more `@Component` implementing `ApprovalRule`; the service does not change.
3. **Manager decision.** Users with the Keycloak realm role `MANAGER` approve or reject pending
   invoices. An invoice that is no longer pending returns `409 Conflict`. The entity has a `@Version`
   column, so when two managers decide at the same moment, one succeeds and the other gets `409`.
4. **Audit.** Every status change publishes an `InvoiceStatusChangedEvent`. The audit module handles it
   with `@TransactionalEventListener(AFTER_COMMIT)`, so a change that was rolled back is never logged.
   Spring Modulith records each event in the `event_publication` table and marks it complete only
   after the listener succeeds, so a failed audit write stays visible instead of disappearing.
   Manager decisions are logged with the manager's user ID; rule decisions have no actor (system).

## API

| Method | Path | Who | Body |
|---|---|---|---|
| `POST` | `/api/v1/invoices` | any authenticated user | multipart: `file`, `vendorName`, `amount` |
| `PUT` | `/api/v1/invoices/{id}/decision` | `MANAGER` | `{"status": "APPROVED" \| "REJECTED", "comment": "..."}` |
| `GET` | `/actuator/health` | public | |

Errors are returned as RFC 7807 problem details: `404` for an unknown invoice, `409` for an invoice
that was already decided or changed concurrently, `400` for an invalid decision.

## Running locally

Requirements: Java 21, Docker with Compose.

```bash
docker compose up -d        # PostgreSQL :5432, MinIO :9000 (console :9001), Keycloak :8081
./gradlew bootRun           # the API on :8080
```

Liquibase creates the schema on startup. On an empty database, a sample manager and one pending
invoice are seeded.

### Keycloak setup (once)

The realm is not exported into the repository yet, so it is created by hand. Open
http://localhost:8081 and sign in as `admin` / `admin`:

1. Create a realm named `ledgerflow`.
2. **Realm roles** → create `MANAGER`.
3. **Clients** → create `invoice-api` (OpenID Connect) and enable *Direct access grants*, so tokens
   can be requested with a username and password for testing.
4. **Users** → create an employee and a manager. Give each an email, first and last name, mark the
   email as verified, and set a non-temporary password. Assign the `MANAGER` role to the manager.

### Try it

```bash
token() {
  curl -s http://localhost:8081/realms/ledgerflow/protocol/openid-connect/token \
    -d grant_type=password -d client_id=invoice-api -d username="$1" -d password="$2" \
    | jq -r .access_token
}

EMPLOYEE=$(token employee <password>)
MANAGER=$(token manager <password>)

# 1200 is above the auto-approval limit, so the invoice waits for a manager
curl -X POST http://localhost:8080/api/v1/invoices \
  -H "Authorization: Bearer $EMPLOYEE" \
  -F file=@invoice.pdf -F vendorName="ACME GmbH" -F amount=1200.00

curl -X PUT http://localhost:8080/api/v1/invoices/<invoice-id>/decision \
  -H "Authorization: Bearer $MANAGER" -H "Content-Type: application/json" \
  -d '{"status": "APPROVED", "comment": "Budget confirmed"}'
```

## Tests

```bash
./gradlew test
```

Integration tests start PostgreSQL and MinIO with Testcontainers, so Docker has to be running.
`InvoiceServiceIT` uploads an invoice and checks the file upload, the rule engine and persistence.

## Project structure

```
src/main/java/org/example/enterpriseinvoiceapproval/
├── controllers/          REST API and error handling
├── security/             JWT validation, Keycloak role mapping
├── modules/
│   ├── workflow/         invoice entity, service, approval rules, events
│   ├── audit/            event listener and audit log
│   └── storage/          MinIO client
├── Identity/             local user records
├── repository/           Spring Data repositories
└── common/               enums and development data seeder
```

## Known limitations and next steps

- The Keycloak realm is set up by hand; exporting it and importing on startup would make the project
  run with one command.
- There are no endpoints yet to list invoices, download a file or read an invoice's audit history.
- Rule parameters (the blacklist keyword and the 500 limit) are hard-coded and could move to
  configuration or the database.
- The `content_hash` column is prepared for duplicate detection but is not filled yet.
- Authorisation relies on Keycloak roles; the `role` column in the local `users` table is informational.
- The credentials in `docker-compose.yml` and `application.yml` are local development defaults.

## Tech stack

Java 21, Spring Boot 3.4, Spring Security (OAuth2 resource server), Keycloak 23, Spring Modulith 1.3,
PostgreSQL and Liquibase, MinIO, Testcontainers, Docker Compose.
