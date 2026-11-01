# Android release signing setup

Google Play requires a signed Android App Bundle. The workflow can sign the release build when the four GitHub Actions secrets below are configured. Until then, the workflow uses Flutter's debug signing configuration for test builds only; **do not upload a debug-signed AAB to Play Console**.

## 1. Create and back up the upload key

On a computer with Java JDK installed, open PowerShell in a private folder and run:

```powershell
keytool -genkeypair -v -keystore hte-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hte-upload
```

Choose strong passwords and keep them private. Back up `hte-upload-key.jks` and its passwords in two secure places. If you lose the upload key, Play App Signing has an upload-key reset process, but do not delete your backup.

## 2. Add GitHub Actions secrets

Open the repository on GitHub:
Settings → Secrets and variables → Actions → New repository secret.

Create these four secrets:

- `ANDROID_KEYSTORE_BASE64`: the keystore file encoded as one Base64 string. In PowerShell, run `[Convert]::ToBase64String([IO.File]::ReadAllBytes("hte-upload-key.jks")) | Set-Clipboard`, then paste the clipboard into the secret value.
- `ANDROID_KEY_ALIAS`: `hte-upload`
- `ANDROID_STORE_PASSWORD`: the keystore password you chose.
- `ANDROID_KEY_PASSWORD`: the key password you chose.

Never put the keystore, passwords, or Base64 value in the repository, an issue, a chat message, or a public file. The workflow reads them as encrypted GitHub Actions secrets and writes the temporary key only inside the runner.

## 3. Build the signed AAB

After all four secrets are saved, open Actions → Build High-Tech Entertainment App → Run workflow. Download the `high-tech-entertainment-android` artifact. It contains both `app-release.apk` (device installation/testing) and `app-release.aab` (Google Play upload). Use the AAB for Play Console.

If the secrets are missing, the build is for device testing only and is debug-signed.

## 4. Play App Signing

When creating the first Play release, enrol in Play App Signing. Google Play uses the app-signing key to sign APKs delivered to users; the key above is the upload key used to authenticate uploads. Keep the upload-key backup safe and do not change the package name after creating the Play Console app.

## Planned application ID

`com.hightechentertainment.app`

Check the generated AAB's application ID before creating the Play Console app. Package names are permanent in Play Console.
