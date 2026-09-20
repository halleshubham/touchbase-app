# TouchBase

Open source Android app to organize contacts, remind you to call people for
upcoming events, run a guided (tap-to-confirm) call queue with per-call
feedback, prefill WhatsApp messages, and sync new contacts to Google —
using Android's own account sync, not a custom API integration.

Kotlin package and `applicationId` are `com.yourname.touchbase`.

## Status: all five phases built

- **Phase 1 — Contacts.** Reads every phone contact via `ContactsContract`,
  mirrors tags/metadata into Room without duplicating name/number data.
  Tag-based filtering, quick-add writes straight into the real address book.
- **Phase 2 — WhatsApp + templates.** Configurable message templates with a
  `{name}` placeholder; a WhatsApp button per contact opens `wa.me` with the
  message pre-filled (falls back from WhatsApp → WhatsApp Business →
  browser). The user still taps Send themselves — WhatsApp exposes no API to
  do that programmatically.
- **Phase 3 — Reminders.** Add a dated reminder (with optional yearly/monthly
  recurrence) per contact. `AlarmManager` fires an exact alarm; the
  resulting notification has a direct **Call** action. A boot receiver
  re-schedules every future reminder after a restart, since exact alarms
  don't survive reboot.
- **Phase 4 — Guided call queue.** Build a queue from a tag filter, then work
  through it one contact at a time: tap **Call** (never silent/automatic —
  see the Play Store note below), the app detects when the call ends and
  prompts for quick feedback (Answered / Voicemail / No answer / Not
  interested / Callback later / Wrong number), then auto-advances.
- **Phase 5 — Contact sync, the device-native way.** No Google Cloud
  project, no OAuth client, no People API. Instead: `AccountsHelper` lists
  the Google account(s) already signed into this phone (`Settings >
  Accounts`); quick-add writes new contacts under whichever account you pick
  in **Sync settings**; Android's own built-in sync adapter — the same one
  the stock Contacts app relies on — pushes that contact to Google
  automatically. `ContactsContentObserver` watches `ContactsContract` for
  any change and enqueues a `WorkManager` job that calls
  `ContentResolver.requestSync()` to nudge that sync to happen right away
  instead of waiting for its normal interval — near-real-time, with zero API
  calls of our own.

## Getting started

1. Open this folder in Android Studio (Koala/2024.1+ recommended).
2. Let Gradle sync — it pulls Compose BOM 2024.06, Room 2.6.1, Hilt 2.51.1,
   WorkManager 2.9.1, Kotlin 1.9.24 automatically. No Google API client
   libraries needed for this build.
3. Run on a device/emulator with API 26+. (An emulator needs a Google
   account added under its own Settings > Accounts for Phase 5 to have
   anything to sync to — a real device with Gmail already signed in works
   out of the box.)
4. Grant Contacts permission on first launch. Phone/call permissions are
   requested separately the first time you open the call queue.
5. Open **Sync settings** (top-right menu on the contacts screen) to pick
   which account quick-added contacts should be saved under.

No `local.properties` edits needed — Android Studio handles the SDK path on
first sync. The Gradle wrapper jar isn't checked in; Android Studio
regenerates it automatically on first open.

## Architecture

- **MVVM + Compose + single-Activity navigation** (`TouchBaseNavHost`):
  Contacts ⇄ Templates / Reminders / Call queue builder / Call queue / Sync
  settings.
- **Repository pattern**: `ContactRepository` is the only class touching both
  `SystemContactsSource` (ContactsContract) and `ContactDao` (Room).
- **Hilt** for DI throughout, including `@HiltWorker` for the sync-trigger
  job and `@AndroidEntryPoint` receivers for alarms/boot rescheduling.
- **Room** as the single local database (`TouchBaseDatabase`) holding
  contacts (shadow rows only), tags, templates, events, and call sessions.
- **No custom backend, no API keys.** Phase 5 deliberately rides on
  Android's existing account sync infrastructure rather than reimplementing
  it — less code, no credentials to manage, and it already handles the hard
  parts (auth refresh, conflict resolution, retry) that a hand-rolled People
  API integration would have to rebuild.

## Play Store note on the call queue

Fully silent, unattended auto-dialing through a contact list is treated as
spam/robocall behavior by Play policy review. `ACTION_CALL` in
`CallQueueScreen` fires only on an explicit user tap of **Call**; the app
never dials on its own. Auto-*advance* to the next contact only happens
after the current call's state returns to idle — the "one after another"
convenience without unattended dialing.

## Known scaffolding to harden before a real release

- **Sync scope**: writing a contact under a Google account and calling
  `requestSync` only *asks* Android to sync soon — it's not guaranteed
  instant, and if the user has disabled auto-sync for that account (Settings
  > Accounts > Google > toggle off), nothing will push until they re-enable
  it. Worth surfacing that state in Sync settings rather than assuming
  success.
- **Call queue feedback → follow-up**: "Callback later" is captured as a
  feedback tag but doesn't yet auto-create a new `Event` reminder — a
  natural next step once you're using the app day-to-day and see how you
  actually want that to behave.
- **Multiple phone numbers per contact**: Phase 1 keeps only the first
  number found per system contact; extend `SystemContactsSource` if you need
  every number, not just one.

## License

MIT — see LICENSE.
