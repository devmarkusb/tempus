# Play Internal Testing Upload

This fork has a separate manual GitHub Actions workflow for uploading the
`tempus` flavor to your own Google Play internal testing track.

The existing GitHub release workflows are unchanged. They still build APKs for
GitHub releases.

## One-time Play Console setup

1. Create a new app in your own Play Console account.
2. Pick a package name you control, for example `dev.example.tempus`.
3. Create an internal testing track and add your tester email addresses.
4. Create or connect a Google Cloud service account under Play Console API
   access, then grant it release access for this app.
5. Upload the first bundle manually if Play Console requires the initial app
   setup to be completed before API uploads.

The package name must be different from the upstream app unless you control the
upstream package in Play Console.

## GitHub configuration

Add this repository variable:

- `PLAY_APPLICATION_ID`: your Play package name, for example
  `dev.example.tempus`

Add these repository secrets:

- `PLAY_SERVICE_ACCOUNT_JSON`: the raw JSON content of the Play service account
  key.
- `PLAY_KEYSTORE_BASE64`: your upload keystore encoded as base64.
- `PLAY_KEY_ALIAS`: the upload key alias.
- `PLAY_KEYSTORE_PASSWORD`: the keystore password.
- `PLAY_KEY_PASSWORD`: the key password.

## Running the upload

Open GitHub Actions, choose **Play Internal Test Upload**, and run it manually.

The workflow builds:

```bash
./gradlew bundleTempusRelease
```

It overrides the Play package name only for that build:

```bash
-PplayApplicationId="$PLAY_APPLICATION_ID"
```

It also sets a CI version code. If you do not pass a `version_code` input, the
workflow uses `25000 + GITHUB_RUN_NUMBER`.
