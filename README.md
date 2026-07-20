<div align="center">

# 🧹 Gmail Contacts Cleaner

**Cleans and deduplicates your Google Contacts across multiple accounts — nightly, automatically, safely.**

[![CI](https://github.com/patbaumgartner/gmail-contacts-cleaner/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/gmail-contacts-cleaner/actions/workflows/ci.yml)
[![Release](https://github.com/patbaumgartner/gmail-contacts-cleaner/actions/workflows/release.yml/badge.svg)](https://github.com/patbaumgartner/gmail-contacts-cleaner/actions/workflows/release.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-blue?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-2.x-brightgreen?logo=spring)](https://spring.io/projects/spring-modulith)
[![Docker Hub](https://img.shields.io/docker/v/patbaumgartner/contacts-cleaner?label=contacts-cleaner&logo=docker&color=blue)](https://hub.docker.com/r/patbaumgartner/contacts-cleaner)

</div>

---

## Table of Contents

- [Why this project?](#why-this-project)
- [How it works](#how-it-works)
- [What gets cleaned](#what-gets-cleaned)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick start — Docker Hub](#quick-start--docker-hub-recommended)
- [Scheduling](#scheduling)
- [Local development](#local-development)
- [Configuration reference](#configuration-reference)
- [Architecture](#architecture)
- [FAQ](#faq)
- [Contributing](#contributing)
- [License](#license)

---

## Why this project?

Contacts accumulate cruft: duplicate phone numbers in three different formats, the same
e-mail address with different capitalization, names padded with stray whitespace from
15 years of device syncing, and empty ghost contacts. Cleaning this manually across
multiple Google accounts is tedious — and the mess comes back.

This project is the spiritual successor of
[gcontacts-cleaner](https://github.com/patbaumgartner/gcontacts-cleaner) (2011, built on
the long-retired GData Contacts API), rebuilt for today's Google: it cleans any number
of configured accounts, runs as a nightly background job, and authenticates with a
simple [app password](https://myaccount.google.com/apppasswords) — no OAuth consent
screens, no Google Cloud project, no API quotas to manage.

> [!NOTE]
> **Why CardDAV and not the People API?** The People API requires OAuth 2.0 with a
> Google Cloud project and periodic re-consent. Google's
> [CardDAV endpoint](https://developers.google.com/people/carddav) — the same protocol
> iOS and macOS use to sync contacts — accepts app passwords via HTTP Basic auth.
> Set it up once per account and it keeps working.

---

## How it works

```
┌──────────────────────────────────────────────────────────────────────┐
│                        contacts-cleaner                              │
│                    (Java 25 / Spring Boot 4)                         │
│                                                                      │
│  for each configured account:                                        │
│                                                                      │
│   1. REPORT addressbook-query   ──►  fetch all vCards + etags        │
│   2. apply cleaning rules            (pure, in-memory)               │
│   3. PUT changed vCards         ──►  etag-guarded, throttled         │
│   4. DELETE empty contacts      ──►  opt-in only                     │
│                                                                      │
│         Basic auth (app password) · google.com/carddav/v1            │
└──────────────────────────────────────────────────────────────────────┘
```

Safety first:

- **Dry run per account** (`dry-run: true`) — logs every change without writing anything.
- **Etag-guarded writes** — a contact modified concurrently (e.g. on your phone) is
  never silently overwritten; the write fails and is retried on the next run.
- **Only changed contacts are written** — an already-clean address book produces zero
  write requests.
- **Destructive rules are opt-in** — note removal and empty-contact deletion are off
  by default.

---

## What gets cleaned

| Rule | Example | Default |
|---|---|---|
| Phone number normalization | `0041 44-668.18 00` → `+41446681800` | ✅ on |
| E.164 formatting (with `phone-region`) | `044 668 18 00` → `+41446681800` (via [libphonenumber](https://github.com/google/libphonenumber)) | ✅ when region set |
| Duplicate phone number removal | `+41446681800`, `0041446681800` → one entry | ✅ on |
| E-mail normalization | ` Jane.Doe@GMAIL.com ` → `jane.doe@gmail.com` | ✅ on |
| Duplicate e-mail removal | keeps the first occurrence | ✅ on |
| Name trimming | `" Jane  Doe "` → `"Jane Doe"` | ✅ on |
| Empty property removal | `EMAIL:`, `ORG:;;`, all-blank `ADR` → dropped | ✅ on |
| Duplicate **contact** detection | two cards sharing a phone/e-mail or near-identical name → **reported, not touched** | ✅ on (report-only) |
| Duplicate **contact** merging | provable duplicates (same name tokens + shared phone/e-mail, e.g. `Tudor Girba` = `Girba Tudor`) → union-merged into one card | ⛔ opt-in |
| Birthday extraction | note `Geburtstag: 12.03.1980` → proper `BDAY` field (existing birthdays never overwritten) | ✅ on |
| Social-network note removal | `XING: xing.com/profile/…`, `Created via LinkedIn`, LinkedIn `Position:/Connected on` blocks stripped — user text preserved | ✅ on |
| Dead-service URL removal | Klout, Gravatar, Google+, Picasa, FriendFeed links dropped; URLs trimmed + deduplicated | ✅ on |
| Invalid e-mail removal | `franz@`, `+41791234567` in the e-mail field → dropped (can never receive mail) | ✅ on |
| E-mail domain verification | DNS lookup: domain gone (NXDOMAIN) → address dropped; timeouts never count | ⛔ opt-in |
| Shared phone number removal | number on ≥ 2 contacts = switchboard/household line → dropped, direct lines kept | ⛔ opt-in |
| Note removal | deletes free-text notes | ⛔ opt-in |
| Empty contact deletion | no phone, e-mail, birthday, address, URL, note **or** org → delete | ⛔ opt-in |

---

## Project structure

```
gmail-contacts-cleaner/
├── src/main/java/com/patbaumgartner/contactscleaner/
│   ├── account/                 # Module: multi-account configuration
│   ├── carddav/                 # Module: Google CardDAV client (RFC 6352)
│   ├── cleaning/                # Module: pure vCard cleaning rules
│   ├── orchestration/           # Module: workflow, scheduler, one-shot runner
│   ├── reporting/               # Module: run summaries via domain events
│   └── config/                  # Shared: HTTP client, scheduling, native hints
├── src/test/java/               # Unit + integration + architecture tests
├── pom.xml
├── docker-compose.yml           # Production — pulls the image from Docker Hub
├── .env.example                 # Configuration template — copy to .env
└── .github/workflows/           # CI, release, dependency review, auto-merge
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| A Google account with **2-step verification** | — | Required to create app passwords |
| An **app password** per account | — | https://myaccount.google.com/apppasswords |
| Docker | 24+ | With Compose v2 — recommended way to run |
| Java 25 (JDK) | 25 | Only for local development without Docker |

---

## Quick start — Docker Hub (recommended)

```sh
# 1. Clone the repository
git clone https://github.com/patbaumgartner/gmail-contacts-cleaner.git
cd gmail-contacts-cleaner

# 2. Create and fill in your configuration
cp .env.example .env
$EDITOR .env   # set accounts[0].email and accounts[0].app-password (keep dry-run=true!)

# 3. Pull the pre-built native image and run
docker compose pull
docker compose up
```

The app cleans all enabled accounts once and exits. Review the dry-run log output,
then set `contacts-cleaner.accounts[0].dry-run=false` and run again.

**Pinning a specific release:**

```sh
VERSION=1.0.0 docker compose up
```

---

## Scheduling

### Option 1: Built-in nightly scheduler (long-running server)

```sh
SPRING_PROFILES_ACTIVE=server docker compose up -d
```

The container stays up and cleans all accounts every night at **03:00 Europe/Zurich**
(configurable via `CONTACTS_CLEANER_SCHEDULER_CRON` / `..._ZONE`). The startup run is
disabled in server mode to avoid duplicates.

### Option 2: External cron (Linux/NAS)

Keep the default one-shot mode and let the host trigger it:

```sh
# Clean contacts every night at 03:00
0 3 * * * cd /path/to/gmail-contacts-cleaner && docker compose pull -q && docker compose up --no-color >> /var/log/contacts-cleaner.log 2>&1
```

---

## Local development

```sh
# Run the application (.env is loaded automatically via spring.config.import)
./mvnw spring-boot:run

# Unit tests only
./mvnw test

# Full verify: formatting, tests, architecture rules, SpotBugs, JaCoCo coverage
./mvnw verify

# Auto-format before committing
./mvnw spring-javaformat:apply
```

Optional read-only integration test against the real Google endpoint:

```sh
CONTACTS_CLEANER_IT_EMAIL=you@gmail.com \
CONTACTS_CLEANER_IT_APP_PASSWORD="abcd efgh ijkl mnop" \
  ./mvnw verify
```

---

## Configuration reference

Copy `.env.example` to `.env` and fill in the required values.

### Accounts (required)

| Variable | Description |
|---|---|
| `contacts-cleaner.accounts[0].name` | Label used in logs (e.g. `personal`) |
| `contacts-cleaner.accounts[0].email` | Google account e-mail |
| `contacts-cleaner.accounts[0].app-password` | App password — never your real password |
| `contacts-cleaner.accounts[0].enabled` | Participate in runs (default `true`) |
| `contacts-cleaner.accounts[0].dry-run` | Compute + log changes only (default `false`, **start with `true`**) |

Add more accounts as `contacts-cleaner.accounts[1].*`,
`contacts-cleaner.accounts[2].*`, … . The same `.env` works for Maven and Docker.

### Cleaning rules

| Variable | Default | Description |
|---|---|---|
| `CONTACTS_CLEANER_NORMALIZE_PHONE_NUMBERS` | `true` | Strip separators, `00` → `+` |
| `CONTACTS_CLEANER_PHONE_REGION` | _(empty)_ | ISO country (e.g. `CH`) — enables E.164 formatting of national numbers |
| `CONTACTS_CLEANER_REMOVE_DUPLICATE_PHONE_NUMBERS` | `true` | Deduplicate per contact |
| `CONTACTS_CLEANER_NORMALIZE_EMAIL_ADDRESSES` | `true` | Lower-case + trim |
| `CONTACTS_CLEANER_REMOVE_DUPLICATE_EMAIL_ADDRESSES` | `true` | Deduplicate per contact |
| `CONTACTS_CLEANER_TRIM_NAMES` | `true` | Trim name whitespace |
| `CONTACTS_CLEANER_REMOVE_EMPTY_PROPERTIES` | `true` | Drop blank `TEL`/`EMAIL`/`URL`/`NOTE`, all-blank `ORG`/`ADR` |
| `CONTACTS_CLEANER_DETECT_DUPLICATE_CONTACTS` | `true` | Report-only: log likely duplicate contact pairs |
| `CONTACTS_CLEANER_MERGE_DUPLICATE_CONTACTS` | `false` | ⚠️ Destructive — auto-merge provable duplicates |
| `CONTACTS_CLEANER_EXTRACT_BIRTHDAYS` | `true` | Promote keyword-tagged note birthdays to `BDAY` |
| `CONTACTS_CLEANER_REMOVE_SOCIAL_NETWORK_NOTES` | `true` | Strip XING/LinkedIn sync lines from notes |
| `CONTACTS_CLEANER_REMOVE_INVALID_EMAILS` | `true` | Drop syntactically broken e-mail addresses |
| `CONTACTS_CLEANER_VERIFY_EMAIL_DOMAINS` | `false` | ⚠️ DNS check — drop addresses of dead domains |
| `CONTACTS_CLEANER_REMOVE_SHARED_PHONE_NUMBERS` | `false` | ⚠️ Destructive — drop switchboard numbers |
| `CONTACTS_CLEANER_SHARED_PHONE_NUMBER_THRESHOLD` | `2` | Contacts sharing a number before it is removed (3 keeps couples' landlines) |
| `CONTACTS_CLEANER_REMOVE_NOTES` | `false` | ⚠️ Destructive — delete notes |
| `CONTACTS_CLEANER_DELETE_EMPTY_CONTACTS` | `false` | ⚠️ Destructive — delete contacts without any information |

### Scheduler (server profile only)

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | _(empty)_ | Set to `server` for the built-in scheduler |
| `CONTACTS_CLEANER_SCHEDULER_CRON` | `0 0 3 * * *` | Nightly at 03:00 |
| `CONTACTS_CLEANER_SCHEDULER_ZONE` | `Europe/Zurich` | Timezone for the cron |
| `CONTACTS_CLEANER_STARTUP_RUN_ENABLED` | `true` (`false` in server) | One-shot run at boot |

---

## HTML report

After every run a **self-contained single-page HTML report** is written to
`reports/cleanup-report-latest.html` (plus a timestamped copy): summary cards per
account, red/green before/after diff per contact, merge and deletion badges, the
duplicate-candidate list, and a live filter box. Run with `dry-run: true`, open the
report, review every change visually — then go live.

---

## Architecture

The application is a [Spring Modulith](https://spring.io/projects/spring-modulith):
module boundaries are verified by tests (`ModularityTests`), coding conventions are
enforced with [Taikai](https://github.com/enofex/taikai)/ArchUnit
(`ArchitectureTests`), and module documentation (PlantUML diagrams + canvases) is
generated on every build under `target/spring-modulith-docs`.

```
orchestration ──► account
      │       ──► carddav ──► account
      │       ──► cleaning
      └──(event)──► reporting
```

Quality gates wired into `./mvnw verify`: Spring Java Format, JUnit 5 + Mockito +
AssertJ, JaCoCo coverage, SpotBugs + FindSecBugs, OWASP dependency-check (release
pipeline), PIT mutation testing, CycloneDX SBOM, and reproducible sorted POMs.
Deployment artifacts are GraalVM **native images** built with Paketo buildpacks —
startup in milliseconds, ~50 MB RSS.

---

## FAQ

**Q: Is an app password safe?**
A: An app password grants scoped device access without exposing your real password and
can be revoked anytime at https://myaccount.google.com/apppasswords. It requires
2-step verification. Store it only in your local `.env` (gitignored).

**Q: Can this destroy my address book?**
A: The default configuration only normalizes formatting and removes exact duplicates
within a contact. Deletions (notes, empty contacts) are opt-in. Start every new account
with `dry-run: true` and read the log. Writes are etag-guarded, so concurrent edits
from your phone always win.

**Q: Does it merge duplicate contacts (two cards for the same person)?**
A: It *detects* them — pairs sharing a phone number, e-mail address, or a near-identical
name (Jaro-Winkler) are reported in the run summary. Merging stays a deliberate human
action in the Google Contacts UI: which card wins and which data survives requires
judgment no heuristic should make for you.

**Q: My account uses Advanced Protection / no app passwords.**
A: App passwords are unavailable under Google's Advanced Protection Program. You would
need to fall back to the People API with OAuth — out of scope for this project.

---

## Contributing

Contributions are very welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before
submitting a pull request.

Quick checklist:

- All tests pass: `./mvnw verify`
- Code is formatted: `./mvnw spring-javaformat:apply`
- New environment variables are documented in `.env.example` and `README.md`

---

## License

Distributed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
