# Google Play Data safety — preparation notes

This is a working checklist based on the current Flutter source and dependencies, not a completed declaration. Re-check the final AAB and every SDK/service before submitting the Play Console form.

## Data visibly processed by the current app

| Data type | Why it is processed | Required? | Notes for Play Console |
|---|---|---|---|
| Email address | Account registration, sign-in and account management | Required to create an account | Supabase Auth receives it. |
| User ID | Identifies the authenticated account/session | Created automatically when an account is created | Supabase Auth assigns this identifier. |
| Password / authentication credentials | Authenticates account creation and sign-in | Required for email/password authentication | Sent securely to Supabase Auth; HTE does not store a plain-text password. |

## Security and deletion notes

- Authentication and backend requests use HTTPS/TLS.
- The app currently has no advertising SDK or third-party analytics SDK in its Flutter dependency list.
- The app does not currently request contacts, location, camera, microphone or photo-library access.
- Account deletion instructions are available at https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account-deletion.html and linked from the app's Account tab.
- The privacy policy is at https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/privacy-policy.html.

## Before submitting the form

1. Inspect the final AAB's merged manifest and dependencies for additional permissions, SDKs or data collection.
2. Confirm the Supabase project's actual authentication, logging, storage and retention settings.
3. In Play Console, answer the collection, sharing, purpose, optionality, encryption and deletion questions to match the final app and backend.
4. Do not claim that no data is collected: email, authentication credentials and a user identifier are processed when a person registers or signs in.
5. Review the completed Data safety preview before submitting it.

The form is completed in Play Console under **App content → Data safety**. Google requires the form and a privacy policy for apps on closed, open and production testing tracks; apps exclusively on internal testing are exempt from the Data safety section. See the official Data safety help page: https://support.google.com/googleplay/android-developer/answer/10787469.
