# Security Policy

## Supported Versions

Only the latest release receives security updates.

| Version | Supported |
| ------- | --------- |
| latest  | ✅        |
| older   | ❌        |

## Handling Credentials

This application authenticates against Google with **app passwords**:

- App passwords are stored only in your local `.env` file, which is gitignored.
  Never commit credentials.
- App passwords never appear in logs; the `GoogleAccount` type redacts them in
  its string representation.
- Revoke app passwords you no longer use at
  https://myaccount.google.com/apppasswords.
- Prefer a dedicated app password per deployment so that a leak can be revoked
  without affecting other devices.

## Reporting a Vulnerability

Please **do not** open a public issue for security vulnerabilities. Instead, use
[GitHub private vulnerability reporting](https://github.com/patbaumgartner/gmail-contacts-cleaner/security/advisories/new)
to report it confidentially.

You can expect an initial response within a few days. Once the issue is
confirmed and fixed, a new release is published and the advisory is disclosed.

## Supply-Chain Measures

- Dependabot keeps dependencies and GitHub Actions up to date (weekly).
- All GitHub Actions are pinned to full commit SHAs.
- OWASP dependency-check and SpotBugs/FindSecBugs run as part of the build.
- A CycloneDX SBOM is generated for every build.
