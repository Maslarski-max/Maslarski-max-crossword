# Play Console: Data safety and App content answers

Answers for the **Data safety** form, based on what this codebase and its SDKs do. Re-check them against
Google's current SDK disclosures (AdMob, Firebase) before each submission and whenever you add an SDK.

## Overview

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | Yes (through AdMob and Firebase) |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS only; cleartext is disabled in `network_security_config.xml`) |
| Do you provide a way for users to request that their data is deleted? | Yes – describe the contact address from the privacy policy |

## Data types

| Data type | Collected | Shared | Purpose | Optional? | Source |
| --- | --- | --- | --- | --- | --- |
| Device or other IDs (Advertising ID, app-instance ID) | Yes | Yes | Advertising or marketing, Analytics, Fraud prevention | No | AdMob, Firebase |
| App interactions | Yes | No | Analytics | No | Firebase Analytics |
| Crash logs | Yes | No | App functionality (stability) | No | Crashlytics |
| Diagnostics | Yes | No | App functionality (stability) | No | Crashlytics |
| Approximate location (IP-derived) | Yes | Yes | Advertising or marketing | No | AdMob / UMP |

Game progress, scores and settings stay on the device (and in the user's own Android backup) and are **not**
"collected" in the Data safety sense.

## App content

- **Ads**: Yes, the app contains ads.
- **Advertising ID**: The manifest declares `com.google.android.gms.permission.AD_ID`; purpose is Advertising and Analytics.
- **Target audience**: 13+ (the app does not set `tagForChildDirectedTreatment`; if you target children you must
  follow the Families policy and configure AdMob accordingly).
- **Privacy policy URL**: `https://maslarski-max.github.io/Maslarski-max-crossword/privacy-policy.html` (published from `docs/` via GitHub Pages).
  Keep it in sync with `privacy_policy_url` in `app/src/main/res/values/strings.xml`.
- **Data deletion**: in-app data is removed by clearing storage/uninstalling; see the privacy policy.
