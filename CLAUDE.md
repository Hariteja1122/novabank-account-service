# CLAUDE.md: novabank-account-service

Part of NovaBank, a banking demo split into six repos that sit side by side in one folder:
novabank-ui (Angular), novabank-api-gateway, novabank-auth-service, novabank-account-service,
novabank-transaction-service (Spring Boot 3.5, Java 21) and novabank-platform (Db2 scripts,
Docker Compose, GCP bootstrap, Postman collection). See ../novabank-platform/README.md.

## Commands
- Build and test: `mvn verify` (unit tests, then `*IT` Db2 integration tests via failsafe)
- Unit tests only: `mvn test` or `mvn verify -DskipITs`. One test: `mvn test -Dtest=AccountServiceTest#method`
- One integration test: `mvn verify -Dit.test=AccountTransferIT -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false`
- Integration tests (`Db2IntegrationTest` subclasses) need Docker and the sibling `../novabank-platform`
  checkout: they start `icr.io/db2_community/db2` with Testcontainers and load `db2/schema/*.sql`
  (override the path with `-Dnovabank.schema.dir=`). First Db2 start takes several minutes.
- Run locally: start Db2 with `../novabank-platform/dev.sh db`, then `mvn spring-boot:run`
  (profile `local` is the default) or the IntelliJ run configuration in `.run/`.
- Whole stack: `../novabank-platform/dev.sh up`

## Conventions
- Java 21, records for DTOs, constructor injection, no Lombok.
- Errors leave the service as RFC 9457 ProblemDetail via GlobalExceptionHandler.
  Throw `ApiException` for business errors.
- The Db2 schema is owned by `novabank-platform/db2/schema`. `ddl-auto` is `none`.
  A column change means editing those SQL scripts, not entities alone.
- Db2 rejects untyped `? IS NULL` parameters, so optional filters use JPA Specifications.
- Money is `BigDecimal` with scale 2. Never double.
- Timestamps are `Instant` and the JVM runs in UTC.
- Secrets come from environment variables. Never commit real values.
- Every POST/PUT/PATCH/DELETE handler under `/api` needs `@Audited` (`AuditCoverageTest` enforces it); GET handlers
  must not have it. Add facts with `Audit.target` / `Audit.detail` / `Audit.reason`, and never pass a password,
  hash, token or request body to them.
- Environments: `application-local.yml`, `application-dev.yml`, `application-preprod.yml`,
  and the Cloud Run definitions in `deploy/environments/`.
