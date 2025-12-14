# Repository Guidelines

## Project Structure & Module Organization
- `dspace/`: CLI scripts (`bin/`), configuration (`config/`, override `local.cfg`), packaged Solr cores (`solr/`), and assembly bits.
- `dspace-api/`: core services and shared test base; sources in `src/main/java`, tests in `src/test/java`.
- `dspace-services/`: cross-cutting infrastructure (configuration, cache, auth).
- `dspace-server-webapp/`: Spring Boot REST API; integration tests live here as `*IT.java`.
- Extensions: `dspace-oai`, `dspace-rdf`, `dspace-iiif`, `dspace-saml2`, `dspace-sword`, `dspace-swordv2`; helper scripts in `scripts/`.

## Build, Test, and Development Commands
- `mvn clean install` builds all modules; unit and integration tests skip by default (`skipUnitTests/skipIntegrationTests=true`).
- `mvn install -DskipUnitTests=false -DskipIntegrationTests=false` runs the full suite.
- `mvn test -DskipUnitTests=false -Dtest=org.dspace.SomeClassTest -DfailIfNoTests=false` targets a unit test; swap `-Dit.test=...` for one integration test.
- `docker compose -p d8 up -d` (repo root) starts the REST stack; add `-f dspace/src/main/docker-compose/docker-compose-angular.yml` to include the Angular UI.

## Coding Style & Naming Conventions
- Java 17 and Maven ≥3.8 enforced by Maven Enforcer.
- Checkstyle: 4-space Java / 2-space XML, K&R braces, no tabs or wildcard imports, 120-char lines, license header, whitespace around tokens.
- Public types/methods need concise Javadocs; imports alphabetized and grouped.
- Naming: packages lowercase, classes UpperCamelCase, methods/fields lowerCamelCase; tests use `*Test` (unit) or `*IT` (integration).

## Testing Guidelines
- Use the provided harness (`AbstractUnitTest`, `AbstractIntegrationTest`, `AbstractIntegrationTestWithDatabase`) for consistent DB/config/filesystem setup; integration tests reset `dspace/config/local.cfg`.
- Favor unit coverage for business logic; add integration tests when touching REST controllers, Spring config, or persistence.
- Keep fixtures small; reuse builders and test utilities within each module’s `src/test/java`.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative summaries (e.g., “Fix language input”); link issues with `Fixes #123` when applicable.
- PRs must pass Checkstyle, ErrorProne, license header checks, and relevant tests; include verification steps.
- Document REST or config changes (update `local.cfg` examples when needed) and justify new dependencies for license compatibility.
- Keep PRs focused and reviewable; attach screenshots only when UI-facing behavior is affected.

## Configuration & Security Tips
- Never commit secrets; use `dspace/config/local.cfg` (copy from `local.cfg.EXAMPLE`) for machine-specific overrides.
- When testing new settings, append to `local.cfg` within integration tests via the provided helpers so cleanup stays automatic.
