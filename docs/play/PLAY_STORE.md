# Google Play — setup notes and drafts

Everything needed to get Intack onto Play: **internal testing** first (up to 100 testers by email, no
public listing), then the **closed test** a new personal account needs, then production.

## Where we are (2026-10-17)

- Developer account **rad-corps** (personal, account ID 5270221815478476760) is paid for.
- Identity documents were submitted on 2026-10-17 and Google is reviewing them ("may take a few days";
  they email the account owner). The contact phone number check unlocks after that.
- No app has been created in Play Console yet.

## Checklist

**Adam (once verified):**
1. Verify the contact phone number (Play Console home).
2. Create the app: name **Intack**, default language English (Australia or United Kingdom), *App* (not
   game), *Free* (see [Price](#price)).
3. **App signing:** choose to **use your own key** and upload the existing one from
   `~/.twodo/release.jks` (Play Console walks you through exporting it with its PEPK tool). Play builds
   and GitHub builds are then interchangeable: people can switch without losing anything.
4. Testing › Internal testing: create an email list of the testers' Google accounts, upload the `.aab`
   that `scripts/release.ps1` prints, roll out, and send testers the opt-in link.
5. For production: a **closed test with at least 12 testers for 14 days** (current rule for new personal
   accounts; check Play Console), then apply for production access.

**Forms** (answers drafted below): app access, ads, content rating, target audience, data safety,
privacy policy URL, foreground service, exact alarms, store listing.

**Assets:**
- Icon 512×512: `docs/play/icon-512.png` (from the launcher icon).
- Phone screenshots: `docs/play/screenshots/`, 1080×2400. **These still show the TwoDo name and the
  older look: retake before uploading.** `scripts/screenshots.sh` makes them from demo data on two
  emulators, but it replaces the debug app's data on both, so don't run it on the bigbox emulator
  while that's in Adam's real groups. Use two fresh AVDs.
- Feature graphic 1024×500: still to make (needed for production, optional for testing).

## App ID

`app.intack` (decided at the rename). It's in the Play Store URL and **can never change** for a Play
listing.

## Price

The app is free on GitHub. The earlier drafts said "pay once", which is the long-term plan, but the
listing must match what Play charges. Until there's a paid version, list it as free and leave "pay
once" out. (A free Play app can't later be made paid; a paid version would be a new listing or an
in-app purchase. Decide before production.)

## Store listing (drafts)

Written for people who just want a shared family calendar and lists, without technical terms.

**App name** (max 30 chars): `Intack — family calendar & lists` (30), or plain `Intack`.

**Short description** (max 80 chars):

> Your family's calendar, lists and messages. No account, no ads, no data mining.

**Full description** (max 4000 chars):

> Keep the family organised — a shared calendar, shopping lists, to-dos and messages — without
> handing your life to a big company.
>
> **No account. No ads.** Nothing to sign up for: open it, name your group and invite your family.
>
> **Your information stays yours.** Your group's calendar and lists live on your family's phones.
> When they travel between phones they're locked with a key only your group has, so nobody in
> between, including us, can read them.
>
> **Share in seconds.** Show a QR code, or send an invite by message. Everyone in the group sees each
> change as it happens: tick something off at the shops and it's ticked at home.
>
> **Works even when you're apart.** Changes reach everyone, even if their phone was asleep or off at
> the time, and without draining the battery.
>
> **What you get:**
> • A shared calendar with reminders and a morning summary of the day
> • Shared lists: tick items off, drag them into order, see who did what
> • Group messages
> • A full history of who changed what, and when
> • Notifications for what you care about, and nothing else
> • Themes and backgrounds, for the whole app or each group
>
> No account. No sign-in. No ads. Just your family's plans, on your family's phones.

**Category:** Productivity (alternative: Lifestyle). **Tags:** family calendar, shared shopping list,
to-do.

**Contact details:** an email address is required (Play shows it publicly). **Website:**
`https://intack.app/`.

**Privacy policy URL:** `https://intack.app/privacy/`

## App content forms

**App access:** All functionality is available without special access (no login).

**Ads:** No, the app doesn't contain ads.

**Content rating questionnaire:** Category *Utility, productivity, communication, or other*. Answer
*No* to violence, sexuality, language, controlled substances, gambling. "Does the app allow users to
interact or exchange content?" **Yes**: group members see each other's names, entries and messages,
but only people the user invited; there's no public content, no contact with strangers and no
location sharing. Expected rating: *Everyone* / PEGI 3.

**Target audience:** 18 and over (keeps it out of the Families programme requirements; families use
it through the adults' phones). Not designed for children.

**Data safety** (check each against Play's current wording at submission):
- **Content** (lists, calendar, messages, names) leaves the phone only **end-to-end encrypted** with the
  group's key, to the people the user invited, through relays that can't read it. Play's guidance
  treats end-to-end encrypted data that only the sender and recipients can read as not collected.
- **Wake pushes (Firebase Cloud Messaging).** The Firebase SDK sends Google a Firebase installation ID
  and a push token, and the app subscribes to topics that are hashes of the group keys. Declare
  **Device or other IDs: collected**, purpose **App functionality**, not shared, not optional (pushes
  are how changes arrive). Check Firebase's own "Data disclosure" page for Messaging at submission and
  match it. Analytics and ad-ID collection are switched off in the manifest.
- **Our servers** (wake server on AWS Lambda, relay.intack.app on AWS Lightsail) see IP addresses, the
  random topic or space codes and encrypted payloads, and keep only short-lived operational logs.
  Nothing is used to identify anyone.
- **Crash reports** are opt-in (Settings) and go only by email the user sends from their own mail app.
  If Play counts that, declare **App info and performance › Crash logs**: collected, optional, app
  functionality, not shared, with the user's email address.
- *Is all user data encrypted in transit?* **Yes.**
- *Can users ask for their data to be deleted?* Content is only on users' devices: remove a list or
  group in the app, or uninstall. Relayed copies expire after 14 days.
- Servers involved (for reviewer notes if asked): our wake server and relay; public Nostr relays;
  public WebTorrent trackers and STUN servers; Google Firebase Cloud Messaging.

**Foreground service** (`FOREGROUND_SERVICE_SPECIAL_USE`): optional **Sync in background**, off by
default since v0.7.1.
- *Description:* "Optional, off by default. Intack keeps a family's shared calendar and lists in sync
  between their phones without storing their content on a server. Normally a push wakes the phone when
  something changes. For phones where pushes don't arrive (for example without Google Play services),
  the user can turn on Sync in background, which keeps the connection open so changes arrive promptly
  while the app is closed. A persistent notification shows while it runs."
- *User impact if deferred:* on those phones, family members' changes don't arrive until the app is
  opened.
- If Play pushes back: remove the option for Play builds and rely on pushes plus the 15-minute
  background check.

**Exact alarms** (`USE_EXACT_ALARM`): Intack is a shared family calendar, and exact alarms deliver its
reminders ("Swimming in 30 minutes") and the morning summary on time. Play allows this permission for
calendar apps. Without it, Android may delay reminders by up to an hour.

**Camera:** used only to scan invite QR codes (declared by the QR library).
