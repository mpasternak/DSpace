# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DSpace is a mature open-source institutional repository platform used by 2000+ organizations worldwide. It manages digital libraries, academic archives, and research outputs.

**Current Version**: 10.0-SNAPSHOT (development branch targeting DSpace 10)
**Technology Stack**: Java 17, Spring Boot 3.5.5, Maven, PostgreSQL (ONLY supported DB), Solr 8.11.4
**Frontend**: Angular (separate repository: https://github.com/DSpace/dspace-angular/)

## Build & Development Commands

### Build
```bash
# Standard build (tests skipped by default for speed)
mvn clean install

# Build with unit tests
mvn install -DskipUnitTests=false

# Build with integration tests
mvn install -DskipIntegrationTests=false

# Fast build without assembly
mvn install -P-assembly
```

### Testing
```bash
# Run specific unit test
mvn test -DskipUnitTests=false -Dtest=full.package.TestClassName#methodName -DfailIfNoTests=false

# Run specific integration test
mvn install -DskipIntegrationTests=false -Dit.test=full.package.TestClassNameIT -DfailIfNoTests=false
```

**Note**: Tests are skipped by default locally but run automatically in CI/CD.

### Docker Development
```bash
# Start backend services
docker compose -p d9 up -d

# Run CLI commands
docker compose -p d9 -f docker-compose-cli.yml run --rm dspace-cli [command]

# Create admin user
docker compose -p d9 -f docker-compose-cli.yml run --rm dspace-cli create-administrator -e test@test.edu -f admin -l user -p admin -c en
```

**Services**: REST API at http://localhost:8080/server

## High-Level Architecture

### Maven Multi-Module Structure
```
dspace-parent/
├── dspace-api/           → Core business logic, models, services
├── dspace-services/      → Service infrastructure
├── dspace-server-webapp/ → Spring Boot REST API (main server)
├── dspace-oai/          → OAI-PMH protocol
├── dspace-sword(v2)/    → SWORD deposit protocols
└── dspace/              → Assembly & configuration
    └── config/          → Configuration files
```

### Core Domain Model
Located in `dspace-api/src/main/java/org/dspace/content/`:
- **Item**: Core content object (publication, dataset, etc.)
- **Collection**: Container for items
- **Community**: Organizational unit
- **Bundle/Bitstream**: File management
- **MetadataValue**: Metadata attached to objects
- **Context**: Request/transaction context

### Service Architecture Pattern
Each entity follows: Entity → Service Interface → ServiceImpl → DAO
- Example: `Item` → `ItemService` → `ItemServiceImpl` → `ItemDAO`
- Services handle business logic, DAOs handle persistence
- Spring dependency injection throughout

### REST API Structure
Located in `dspace-server-webapp/src/main/java/org/dspace/app/rest/`:
- **model/**: REST resource representations
- **repository/**: Repository pattern implementations
- **converter/**: Entity ↔ REST converters
- Uses HAL/JSON format with HATEOAS

## Configuration

### Configuration Hierarchy
1. Base: `dspace/config/dspace.cfg`
2. Module configs: `dspace/config/modules/*.cfg`
3. Local overrides: `dspace/config/local.cfg` (highest priority)

### Spring Configuration
- Bean definitions: `dspace/config/spring/api/*.xml` (56 files)
- REST configs: `dspace/config/spring/rest/*.xml`
- Boot config: `dspace-server-webapp/src/main/resources/application.properties`

## Database & Migrations

- **PostgreSQL ONLY** - No MySQL/MariaDB support
- Flyway migrations: `dspace-api/src/main/resources/org/dspace/storage/rdbms/sqlmigration/postgres/`
- Database cleaned during builds (disable with `db.cleanDisabled=false`)

## Code Standards

### Checkstyle Rules
- 4-space indents for Java, 2-space for XML (NO TABS)
- K&R brace style required
- Max line length: 120 characters
- Javadocs required for all public classes/methods
- No wildcard imports

### PR Requirements
- Keep PRs < 1,000 lines of code
- Must pass all automated tests
- Must include tests for new functionality
- Must pass Checkstyle validation
- Document REST API changes in RestContract

## Key Integration Points

- **Handle System**: Persistent identifiers
- **ORCID**: Researcher identifiers
- **OAI-PMH**: Metadata harvesting
- **SWORD/SWORDv2**: Deposit protocols
- **SAML2**: Authentication
- **IIIF**: Image serving
- **Solr**: Search and discovery