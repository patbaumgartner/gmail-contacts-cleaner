# Contributing to Gmail Contacts Cleaner

Thank you for taking the time to contribute! This guide will help you get up and
running quickly and explains the conventions used in this project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Getting started](#getting-started)
- [Development workflow](#development-workflow)
- [Commit messages](#commit-messages)
- [Pull request guidelines](#pull-request-guidelines)
- [Project conventions](#project-conventions)

---

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating you agree to abide by its terms. Please report unacceptable
behaviour to the maintainer.

---

## Ways to contribute

- **Bug reports** — found something broken? Open an issue using the
  [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) template.
- **Feature requests** — have an idea? Use the
  [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) template.
- **Code contributions** — fix a bug, implement a feature, improve tests or
  documentation. See the workflow below.
- **Documentation** — improve the README, add examples, fix typos.

---

## Getting started

### 1. Fork and clone

```sh
# Fork the repo on GitHub, then:
git clone https://github.com/YOUR_USERNAME/gmail-contacts-cleaner.git
cd gmail-contacts-cleaner
git remote add upstream https://github.com/patbaumgartner/gmail-contacts-cleaner.git
```

### 2. Configure your environment

```sh
cp .env.example .env
# Add a test account with dry-run enabled — never develop against your main
# address book without dry-run!
```

### 3. Build and test

```sh
./mvnw verify
```

---

## Development workflow

```sh
# Create a feature branch from main
git checkout -b feature/my-feature   # or fix/my-fix, docs/my-docs

# Make your changes, then:
./mvnw spring-javaformat:apply   # auto-format before committing
./mvnw verify                    # format check + tests + architecture rules + SpotBugs + JaCoCo

# Commit and push
git commit -m "feat: add support for ..."
git push origin feature/my-feature

# Open a Pull Request on GitHub
```

---

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>
```

| Type | When to use |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no logic change |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or improving tests |
| `build` / `ci` | Build system or CI changes |
| `deps` | Dependency updates |
| `chore` | Anything else |

---

## Pull request guidelines

- Keep PRs focused — one logical change per PR.
- All CI checks must pass (`./mvnw verify` locally first).
- Add tests for new behaviour; keep coverage from dropping.
- Update `.env.example` and `README.md` when adding configuration.
- Fill in the pull request template.

---

## Project conventions

- **Architecture** — the app is a Spring Modulith. Respect module boundaries;
  `ModularityTests` and `ArchitectureTests` (Taikai/ArchUnit) enforce them. Public
  module APIs live at the module root; internals are package-private.
- **Formatting** — [Spring Java Format](https://github.com/spring-io/spring-javaformat);
  run `./mvnw spring-javaformat:apply`.
- **Safety** — anything that deletes user data must be opt-in and covered by
  dry-run support and tests.
- **Documentation** — public types carry Javadoc explaining the *why*, not just
  the *what*.
