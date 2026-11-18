# Agent Guidelines — Legal & Platform Constraints

These rules apply to everyone who builds this project. They exist to keep it clearly an **interoperability tool** for parents managing their own family's Nintendo Switch Parental Controls, and not something that Nintendo, Apple or Google would have grounds to act against. If a task conflicts with a rule here, stop and ask the owner before going ahead.

## Project framing

- This is an **unofficial, independent, open-source client** for the user's own Nintendo Account parental-control settings.
- It is **not affiliated with, endorsed by, or sponsored by Nintendo**. Never write code, copy, metadata or commit messages that suggest otherwise.
- The goal is a better UX and reliability on top of the existing API. No new capabilities that Nintendo's own app doesn't offer the account holder.

## Hard rules (never do)

1. **No Nintendo branding.**
   - Don't use "Nintendo", "Switch", "Joy-Con" or similar marks in the app name, bundle ID / package name, icon, splash screen or store title.
   - Don't use Nintendo logos, fonts, mascots, character art, console renders or screenshots of Nintendo's app.
   - In descriptive text only, you may write "works with Nintendo Switch Parental Controls" (nominative use), always with the disclaimer below.
2. **No Nintendo code or assets in the repo.**
   - Don't commit decompiled or disassembled code, copied resources, or APKs/IPAs (or extracts from them).
   - Don't bundle game icons or artwork. If the API returns image URLs, load them at runtime and cache them on the device only.
3. **No circumventing protections.**
   - Don't implement, document or link to ways around request signing, app attestation (Play Integrity / App Attest), certificate pinning, DRM or encryption.
   - If the API starts requiring any of these, **stop and report to the owner**. Don't try to work around it.
4. **No hardcoded secrets pulled from Nintendo's app.**
   - Put every client ID, redirect URI, user-agent string and API base URL in **one config module** (e.g. `lib/config/nintendo_api.dart`), with a comment that says where each value came from (e.g. "same as used by the open-source `pynintendoparental` project").
   - Nothing secret beyond what the public open-source projects already use.
5. **No backend that touches credentials.**
   - All authentication and API calls go **directly from the device to Nintendo**. No proxy, relay or analytics server in between.
   - Store tokens only in iOS Keychain / Android Keystore (e.g. `flutter_secure_storage`). Never in plain prefs, logs, crash reports or backups.
6. **No unnecessary data collection.**
   - The data concerns children (GDPR applies; COPPA if there are US users).
   - No third-party analytics, ad SDKs or tracking.
   - If crash reporting is added, it must be opt-in and must scrub tokens, account IDs, child names and play data.
7. **No monetization hooks.** No paywalls, ads or in-app purchases. Don't add any without explicit approval from the owner.

## Required behaviour

- **Be gentle with the API.**
  - Poll at intervals no more aggressive than the official app's: refresh when the app comes to the foreground, plus a sensible minimum interval (≥ 5 min in the background, if background refresh exists at all).
  - Back off exponentially on errors and 429s. Never retry in a tight loop.
- **Use a clear, honest user-agent.** Identify the client honestly (e.g. `<AppName>/<version> (+<repo URL>)`) unless the API rejects it. In that case, flag it to the owner rather than silently imitating Nintendo's app.
- **Fail gracefully.** Isolate all Nintendo API access behind one client/repository layer, with typed errors. When the API changes or auth breaks, show a clear message ("Nintendo's service changed; update the app or use the official app"). No crashes or silent data loss.
- **Confirm destructive actions.** PIN changes, locks and suspensions need explicit confirmation and must show what will happen.
- **Inform the user at onboarding.** Before login, show a one-time screen saying that:
  - this is an unofficial app;
  - using it may conflict with Nintendo's terms of service;
  - Nintendo could restrict their account;
  - credentials go only to Nintendo.

  Require the user to acknowledge it.

## Required text

Use this disclaimer in the README, the app's About screen and any store listing:

> This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by Nintendo Co., Ltd. "Nintendo" and "Nintendo Switch" are trademarks of Nintendo. This app communicates only with Nintendo's servers using your own account; use it at your own risk.

## Repository hygiene

- License: a permissive OSS license (MIT or Apache-2.0, as the owner chooses). Credit the upstream projects the API knowledge came from (e.g. `pynintendoparental`).
- `.gitignore` must cover tokens, `.env`, captured HTTP traffic (HAR files, mitmproxy dumps) and test account data.
- **Test fixtures:** use synthetic, made-up JSON. Never commit real API responses containing real account IDs, names, device IDs or play history.
- Docs and the blog post describe **how the app talks to the API**, framed as interoperability. No "how to hack" content, and no guides to intercepting Nintendo's official app.
- Commit messages and issues stay neutral. No disparaging language about Nintendo.

## Escalate to the owner when

- the API adds signing, attestation, pinning or anything that looks like a protection measure;
- a feature would need data or actions the official app doesn't give the account holder;
- something would need a server component;
- preparing an App Store / Play Store submission (naming, screenshots and listing text need review);
- any contact from Nintendo, GitHub (DMCA) or a store reviewer arrives.
