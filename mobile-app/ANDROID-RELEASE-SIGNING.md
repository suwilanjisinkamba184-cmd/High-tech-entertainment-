# Android release signing setup

The Android GitHub Actions workflow requires a configured upload keystore and all four repository secrets. **If any secret is missing or empty, the Android workflow fails. It does not fall back to debug signing.**

## 1. Create and back up the upload key

On a computer with Java JDK installed, open PowerShell in a private folder and run:

```powershell
keytool -genkeypair -v -keystore hte-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hte-upload
```

Choose strong passwords. Back up `hte-upload-key.jks` and its passwords in at least two secure places. Do not upload the keystore to GitHub or send it in chat. Losing the upload key can complicate future app updates; if Play App Signing is enabled, Google provides an upload-key reset process.

The current GitHub workflow decodes the Base64 keystore to a temporary `.p12` file on the runner. Base64 is encoding, not encryption, so keep the encoded value secret.

## 2. Add the four GitHub Actions secrets

In the GitHub repository, open:

**Settings → Secrets and variables → Actions → New repository secret**

Create these exact repository secrets:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore file encoded as one Base64 string |
| `ANDROID_KEY_ALIAS` | The key alias; with the command above, use `hte-upload` |
| `ANDROID_STORE_PASSWORD` | The keystore password you created |
| `ANDROID_KEY_PASSWORD` | The key password you created |

To copy the Base64 value using PowerShell, run this in the folder containing the keystore:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("hte-upload-key.jks")) | Set-Clipboard
```

Paste the clipboard contents into the `ANDROID_KEYSTORE_BASE64` secret.

Never put the keystore, passwords or Base64 value in repository files, issues, public releases or chat messages. GitHub Actions secrets are injected into the workflow at build time.

## 3. Run the Android build

After saving all four secrets:

1. Open the repository's **Actions** tab.
2. Select **Build High-Tech Entertainment App**.
3. Select **Run workflow** on the `main` branch.
4. Wait for the Android job to complete.
5. Download the `high-tech-entertainment-android` artifact.

The artifact contains:

- `app-release.apk` — for installation and testing on Android devices.
- `app-release.aab` — the Android App Bundle format used for Google Play submission.

The workflow also publishes the APK as a GitHub Release when it runs on a non-pull-request event and signing is configured.

If the Android job reports missing secrets, check that all four names are spelled exactly as shown and that each value is saved under **Repository secrets**. Re-run the workflow after correcting them.

## 4. Google Play App Signing

When creating the first Play Console release, enrol in Google Play App Signing. Google Play uses the app-signing key to sign APKs delivered to users; the upload key configured above authenticates app bundle uploads.

Keep the upload-key backup safe. Do not change the Android application ID after creating the Play Console app.

## Planned application ID

```
com.hightechentertainment.app
```

The workflow generates the Android platform scaffold and configures the application identity. Check the application ID in the generated AAB before creating the Play Console app. Package names are permanent in Play Console.
