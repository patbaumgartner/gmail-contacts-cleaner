# Gmail Contacts Cleaner Agent Guide

## Start Here

- Use Java 25 and the Maven wrapper. Run `./mvnw test` for unit-test changes and `./mvnw verify` before completing production code changes.
- Run `./mvnw spring-javaformat:apply` before final validation when Java or `pom.xml` changes. Do not enable editor format-on-save for Java; it conflicts with Spring Java Format. See [.vscode/settings.json](.vscode/settings.json).
- Follow the contributor workflow, commit convention, and documentation expectations in [CONTRIBUTING.md](CONTRIBUTING.md).

## Architecture

- Keep the Spring Modulith modules isolated: `account`, `carddav`, `cleaning`, `orchestration`, and `reporting`; `config` is shared infrastructure. `ModularityTests` and `ArchitectureTests` enforce these boundaries during `verify`.
- Keep cleaning rules pure and side-effect free in `cleaning`; put CardDAV and People API I/O in `carddav`; coordinate account workflows in `orchestration`; report outcomes through domain events in `reporting`.
- Expose only intended module APIs at each module root. Keep implementation details package-private.

## Tests And Safety

- Add focused unit tests for cleaning behavior. Use `*Test`/`*Tests` for unit tests and `*IT`/`*IntegrationTest` for integration tests; Maven selects and configures their Spring profiles automatically.
- Preserve safety guarantees for any user-data mutation: destructive behavior must be opt-in, dry-run must not write, and writes must remain etag-guarded.
- Never log, commit, or add tests containing app passwords, OAuth client secrets, refresh tokens, authorization codes, or values from `.env`. Use dry-run and dedicated test accounts for live integration testing.

## Reference Material

- [README.md](README.md) documents cleaning-rule semantics, configuration, and local commands.
- [docs/oauth2-setup.md](docs/oauth2-setup.md) covers OAuth setup and refresh-token recovery.
- [src/test/java/com/patbaumgartner/contactscleaner/ModularityTests.java](src/test/java/com/patbaumgartner/contactscleaner/ModularityTests.java) and [src/test/java/com/patbaumgartner/contactscleaner/ArchitectureTests.java](src/test/java/com/patbaumgartner/contactscleaner/ArchitectureTests.java) are the source of truth for enforced structure.