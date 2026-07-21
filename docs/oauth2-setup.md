# Google OAuth 2.0 Setup

This guide configures the optional Google People API features:

- importing **Other contacts** into My Contacts;
- preferring a Google profile image over a contact-specific image.

CardDAV-only cleanup does not need OAuth. It uses the Google app password configured for the account instead.

## Before You Start

You need access to the Google account being cleaned and a Google Cloud project. Keep client secrets,
authorization codes, access tokens, and refresh tokens out of source control, issue trackers, chat
messages, reports, and shell history. Store production values only in the ignored `.env` file.

Each Google account needs its own refresh token. Multiple accounts may use the same OAuth client when
they belong to the same Google Cloud project.

## 1. Create an OAuth Client

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select a project.
3. Enable the **Google People API** under **APIs & Services > Library**.
4. Configure the **OAuth consent screen**. Add every Google account that will authorize the app as a
   test user while the app is in testing.
5. Under **APIs & Services > Credentials**, create an OAuth 2.0 client ID of type **Web application**.
6. Add `http://localhost:8089` as an authorized redirect URI. Use the exact URI in the authorization
   and token-exchange commands below.

Record the client ID and client secret somewhere private. Do not put them in `.env.example` or commit
them to Git.

## 2. Authorize One Google Account

The application exchanges refresh tokens automatically during a run, but the initial browser consent
flow is manual.

Start a temporary local listener before authorizing so the browser can redirect back to your machine:

```sh
python3 -m http.server 8089
```

Build an authorization URL with the client ID substituted. Keep the two scopes exactly as shown:

```text
https://accounts.google.com/o/oauth2/v2/auth?client_id=<CLIENT_ID>&redirect_uri=http://localhost:8089&response_type=code&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcontacts.other.readonly%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcontacts&access_type=offline&prompt=consent
```

Open the URL in a browser, sign in as the account being cleaned, and approve the consent screen. Copy
the `code` query parameter from the redirect URL. An authorization code is short-lived and can be used
only once.

Exchange it immediately for a refresh token. Replace each placeholder locally:

```sh
curl --request POST https://oauth2.googleapis.com/token \
  --data-urlencode "client_id=<CLIENT_ID>" \
  --data-urlencode "client_secret=<CLIENT_SECRET>" \
  --data-urlencode "code=<AUTHORIZATION_CODE>" \
  --data-urlencode "redirect_uri=http://localhost:8089" \
  --data-urlencode "grant_type=authorization_code"
```

The response includes a short-lived `access_token` and, when offline access was granted, a long-lived
`refresh_token`. The application only needs the refresh token. Stop the temporary listener after
copying the code.

## 3. Configure the Application

For the relevant account in the ignored `.env`, set the OAuth values and enable the feature or features
needed:

```properties
contacts-cleaner.accounts[0].oauth-client-id=<CLIENT_ID>
contacts-cleaner.accounts[0].oauth-client-secret=<CLIENT_SECRET>
contacts-cleaner.accounts[0].oauth-refresh-token=<REFRESH_TOKEN>
contacts-cleaner.accounts[0].import-other-contacts=true
contacts-cleaner.accounts[0].prefer-google-profile-photos=true
```

`import-other-contacts` and `prefer-google-profile-photos` are independent. Both are skipped in a dry
run. The importer needs both scopes: `contacts.other.readonly` to list Other contacts and `contacts` to
copy them into My Contacts. The photo feature uses the `contacts` scope.

Run first with `dry-run=true` to validate the configuration and review the report. Turn off dry-run only
after reviewing the result.

## Access Token Refresh During Normal Runs

No manual access-token refresh is needed. At the start of a People API operation, the application posts
the configured refresh token to `https://oauth2.googleapis.com/token` and uses the returned access
token for that run.

A refresh token is not an eternal credential. Google can invalidate it when the user revokes consent,
the OAuth client is deleted or its secret is rotated, a security event occurs, or an app remains in the
OAuth consent screen's testing state. Testing refresh tokens commonly expire after seven days.

## Replace an Invalid or Expired Refresh Token

When the log reports an OAuth token refresh failure, treat the configured refresh token as unusable:

1. Leave `dry-run=true` and disable People API features for the affected account until reauthorization
   is complete.
2. Repeat [Authorize One Google Account](#2-authorize-one-google-account), using `prompt=consent` in
   the authorization URL.
3. Exchange the new authorization code immediately.
4. Replace only `oauth-refresh-token` in `.env` with the newly returned value. Also replace the client
   ID and secret if the OAuth client was rotated.
5. Run a dry run to verify the new credentials, then re-enable the desired feature.

Google may omit a refresh token when the same account has already granted the same client and scopes.
The `prompt=consent` parameter in this guide asks for consent again. If Google still does not return a
refresh token, revoke the app's access for that Google account at
[Google Account third-party connections](https://myaccount.google.com/connections), then repeat the
flow. Revoking access invalidates every refresh token issued for that client and account.

## Security Checklist

- Keep `.env` ignored; use placeholders only in `.env.example`.
- Do not commit, paste, or log OAuth client secrets, authorization codes, access tokens, or refresh tokens.
- Revoke the old token before or immediately after replacing a credential that may have been exposed.
- Rotate the OAuth client secret in Google Cloud if it was exposed.
- Use a separate OAuth client for separate environments when practical.
