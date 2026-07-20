# Local test data

This directory holds a **real Google Contacts export** used by the optional analysis
integration test. Everything in here except this README is **gitignored** — contact
exports contain personal data and must never be committed.

## What goes here

| File | Description |
|---|---|
| `contacts.csv` | A "Google CSV" export of your own address book |

## How to create the export

1. Open [contacts.google.com](https://contacts.google.com)
2. Select **Export** in the left sidebar
3. Choose **Google CSV** as the format
4. Save the downloaded file as `test-data/contacts.csv`

## What it is used for

`GoogleCsvExportAnalysisIT` (in `src/test/java/.../cleaning/`) converts each CSV row
into a vCard and runs the **production cleaning rules** against your real address
book — fully offline, nothing is sent anywhere, nothing is modified. It prints:

- per-rule hit counts (how many contacts each rule would change),
- sample before/after diffs for visual verification,
- duplicate contact candidates,
- a **residual-dirt scan**: patterns that survive all current rules — the ideas
  backlog for the next cleaning rule.

Run it with:

```sh
./mvnw test-compile failsafe:integration-test \
  -Dit.test=GoogleCsvExportAnalysisIT -Djacoco.skip=true -Dspotbugs.skip=true
```

The test is skipped automatically (JUnit assumption) when `contacts.csv` is absent,
so CI and contributors without an export are unaffected.

> [!WARNING]
> The report prints contact names, phone numbers and note excerpts from **your own
> data** to the local console. Don't paste the output into issues or pull requests.
