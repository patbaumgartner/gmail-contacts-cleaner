# Changelog

All notable changes to this project are documented in this file. The format is based
on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Name repair sanitizes every name component: boundary quotes (even one-sided
  strays, typographic variants included), emojis and invisible characters are
  removed, whitespace collapsed; inner nickname quotes are preserved

## [1.0.0] - 2026-07-20

Initial release — the spiritual successor of gcontacts-cleaner, rebuilt for today's Google.

### Added

**Core**
- Google CardDAV client (RFC 6352) authenticating with per-account **app passwords** —
  no OAuth, no Google Cloud project: `PROPFIND Depth:1` listing +
  `addressbook-multiget` in batches of 25, etag-guarded `PUT`/`DELETE`, request
  throttling, bisect-and-retry on server errors
- **Multiple configurable accounts**, each with `enabled` and `dry-run` flags
- Nightly built-in scheduler (`server` profile, cron/zone configurable) or one-shot
  startup run for external cron / NAS schedulers
- **Single-page HTML report** after every run: per-account summary, red/green
  before/after diffs, deletion badges, duplicate candidates, live filter, dark mode

**Cleaning rules (non-destructive, on by default)**
- Phone normalization: separators, `00` → `+`, invisible Unicode bidi marks, `++`;
  optional **E.164 formatting** via libphonenumber with configurable `phone-region`
- Duplicate phone/e-mail removal within a contact
- E-mail normalization (lower-case, trim) and syntactically-invalid e-mail removal
- Name whitespace trimming and **flipped-name repair** (swaps given/family only when
  the contact's own e-mail proves the order)
- Empty-property removal (blank `TEL`/`EMAIL`/`URL`/`NOTE`, all-blank `ORG`/`ADR`)
- Social-network sync-note removal (XING/LinkedIn profile URLs, LinkedIn
  `Position:`/`Connected on` import blocks) — user-written text preserved
- Dead-service URL cleanup (Klout, Gravatar, Google+, Google Profiles, Picasa,
  FriendFeed) plus URL trim + dedup
- Birthday extraction: promotes keyword-tagged note birthdays to a proper `BDAY`
- Report-only **duplicate contact detection** (shared phone/e-mail, near-identical or
  word-flipped names) — merge with Google's own "Merge & fix"

**Cleaning rules — later additions (see below for the destructive set)**
- Opt-in fax number removal (`remove-fax-numbers`): drops `TEL;TYPE=FAX` entries
  (work and home)
- Custom-field removal by label (`remove-custom-fields`, default `Age`): deletes
  Apple-style `X-ABLabel` groups and `X-<label>` properties
- XING profile URLs added to the URL cleanup (sync-app spam)
- Export analysis prints a custom-field label inventory
- Social profile URLs added to the URL cleanup (Facebook, Twitter/X, Bluesky,
  Mastodon, Instagram, Threads, TikTok, Snapchat, Flickr, Vimeo, Foursquare,
  about.me); LinkedIn and personal sites are kept
- Organization removal by name (`remove-organizations`, empty by default) for
  defunct companies
- Label normalization (`normalize-labels`, on by default): custom e-mail/address
  labels become standard vCard types ('Geschäftlich' → WORK) or are dropped for
  the default type ('Internet email', 'Obsolete', ...)
- Redundant address removal (`remove-redundant-addresses`, on by default): keeps
  the richer of two addresses when one is a subset of the other

- Organization canonicalization (`canonicalize-organizations`, on by default):
  cross-contact pass unifying company spellings to the majority variant
- Self-organization removal: `ORG` repeating the person's own name is dropped
- Dangling-title removal: titles without an organization (incl. orphaned by
  organization removal) are dropped
- Geo-coordinate address removal: coordinate-only addresses are dropped
- Custom-field default list extended to `Age,Photo`
- URL deduplication now ignores scheme, `www.` and trailing-slash differences
- Label normalization extended to URL labels; orphaned `X-ABLabel`s are swept
- Junk name-suffix removal (on by default): parenthesized messenger-import
  fragments like `(JIRA)` are dropped, honorific suffixes are kept
- Name repair (`repair-names`, on by default): ALL-CAPS names get smart casing
  (`McDonald`/`O'Brien`/`van der` aware), known prefixes are canonicalized
  (`Dr` → `Dr.`), e-mail addresses stuck in name fields are moved to e-mails
- Phone type correction (`correct-phone-types`, on by default): mobile/landline
  classification verified against the numbering plan via libphonenumber
- Label normalization covers e-mail, phone and address labels (`Mobil` → CELL,
  fax labels → FAX type, unknown labels like `WhatsApp`/`Old` → default type)
- Name repair also flips "Last, First" display names (`Muster, Max` →
  `Max Muster`), populating empty given/family fields; company-style names
  and contradicting structured names are never touched
- Wrapping-name quote removal and `Last, First` name repair can be configured
  independently (`remove-wrapping-name-quotes` and `repair-comma-formatted-names`)
- Instant-messenger removal (`remove-instant-messengers`, on by default): drops
  IMPP handles of dead networks (ICQ, AIM, Yahoo, Skype, ...)
- Empty-property rule also removes blank and exact-duplicate extended properties
  (e.g. `X-GENDER:Male` repeated seven times)
- Label normalization sweeps group-less `X-ABLabel` debris
- Additional-organizations removal (`remove-additional-organizations`, opt-in):
  keeps the primary organization, drops imported employment history
- Documented CardDAV limitation: Google does not expose CSV custom fields
  (`Age`, `Other Organizations`) to sync clients — one-time CSV cleanup required

**Cleaning rules (destructive, opt-in)**
- Country-invalid phone number removal (libphonenumber validation)
- Shared phone number removal (numbers on ≥ 2 contacts = switchboard/household line)
- DNS-verified e-mail domain check (removes addresses only on authoritative NXDOMAIN)
- Note removal and empty-contact deletion (no phone, e-mail, birthday, address, URL,
  note or organization)
- Birthday-only contact deletion (`delete-birthday-only-contacts`): removes contacts
  with a birthday but no phone, e-mail, address, URL, note, or organization

**Engineering**
- Spring Boot 4.1 / Spring Modulith 2.1 / Java 25, GraalVM native image via Paketo
  buildpacks
- 186 unit tests + integration tests (embedded fake CardDAV server, optional
  read-only test against real Google), Spring Modulith verification, Taikai/ArchUnit
  architecture rules, SpotBugs + FindSecBugs, JaCoCo, PIT, OWASP dependency-check,
  CycloneDX SBOM
- Offline analysis IT against a local Google CSV export (`test-data/`, gitignored)
  with per-rule hit counts, diffs and a residual-dirt scan
- GitHub Actions CI/release pipelines, Dependabot, dependency review, auto-merge

### Fixed (during pre-release hardening)
- E-mail domain verification: DNS NODATA (domain exists, no MX/A records) is no
  longer treated as non-existence, and NXDOMAIN must be confirmed by a second
  lookup before any address is removed
- Fax removal also catches Apple-style custom labels (`X-ABLabel: Work Fax`)
- Validated GraalVM native image build (libphonenumber resource hints)
- Google CardDAV fetch: PROPFIND + batched `addressbook-multiget` with `Depth: 0`
  and bisect-and-retry on server errors (the original single `addressbook-query`
  returned empty results)

[Unreleased]: https://github.com/patbaumgartner/gmail-contacts-cleaner/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/patbaumgartner/gmail-contacts-cleaner/releases/tag/v1.0.0
