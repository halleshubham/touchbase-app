# Development log

Running notes on non-obvious decisions and bug root causes. Inline code
comments should stay to one line and point here for the "why" instead of
explaining it in place.

## 2026-09-20

- **App/package rename (ContactKeeper -> TouchBase).** Display name, Kotlin
  package (`com.yourname.contactkeeper` -> `com.yourname.touchbase`),
  `applicationId`, and all `ContactKeeper*` class names were inconsistent -
  only the display name had been updated. Renamed everything to match.

- **GitHub Actions CI.** Added `.github/workflows/android-build.yml` to build
  a debug APK on every push. `android-actions/setup-android` isn't needed
  and actively conflicts with `ubuntu-latest`'s preinstalled SDK - dropped it.

- **Missing launcher icon.** `AndroidManifest.xml` referenced
  `@mipmap/ic_launcher`, but a stray literal directory named
  `{values,mipmap-anydpi-v26}` (unexpanded shell brace-expansion from
  whoever scaffolded `res/`) sat where `mipmap-anydpi-v26` should have been.
  Added a real adaptive icon.

- **Startup crash.** `TouchBaseApp.onCreate()` called
  `ContactsContentObserver.startWatching()` unconditionally at process
  start, before READ_CONTACTS/WRITE_CONTACTS are ever granted -
  `registerContentObserver` on `ContactsContract` throws `SecurityException`
  without that permission. Moved the call into `ContactListViewModel`'s
  `init` (only ever constructed behind `ContactsPermissionGate`), plus a
  defensive permission check inside `startWatching()` itself.

- **N+1 query on every contacts-screen open.**
  `ContactRepository.refreshFromSystemContacts()` called
  `dao.findBySystemId()` once per contact, sequentially, and this whole
  refresh re-runs every time the Contacts screen appears (not just first
  launch). Replaced with one batched `findBySystemIds()` query.

- **Lazy loading + indexing.** Contact list now loads via Paging3 instead of
  the whole table at once. Added indices: `Contact.systemContactId`,
  `Contact.rawTimestampAdded`, `ContactTagCrossRef.tagId` (the last one
  Room's own KSP output had already flagged as unindexed). Filtering/sorting
  moved into SQL (`ContactDao.pagedContacts`) since paging can't work over a
  list that's still being reassembled in Kotlin.
  `QueueBuilderScreen` needs the *full* contact list (to resolve every
  contact id for a tag before starting a call-queue session), so it got its
  own `QueueBuilderViewModel` instead of sharing the now-paged
  `ContactListViewModel`.

- **Saved lists.** Added as named, reusable presets over the existing
  tag/date/sort controls (`SavedFilter` entity), not a second per-contact
  tagging system - the app already has Tags for that.

- **Theme was M3 in name only.** Only `primary`/`secondary` were set, and
  both matched M3's own generic baseline-purple defaults exactly, so
  overriding them was a no-op - every other color role, and the whole
  scheme, was stock. Replaced with a full hand-authored teal color-role
  palette (light + dark) plus Material You dynamic color on Android 12+.

- **UI decluttering.** Tag/sort/date/lists controls were about to become
  four stacked chip rows. Consolidated to one visible tag row (most used)
  + a filter icon opening a bottom sheet for sort/date/saved-lists, per
  current mobile UI guidance on progressive disclosure over stacking
  controls.

- **Bug: old contacts showing as recently added.** `rawTimestampAdded` was
  populated from `ContactsContract.CONTACT_LAST_UPDATED_TIMESTAMP`, which is
  NOT a creation date - it moves whenever the OS touches a contact for any
  reason (a call, a sync, a photo/label edit, contact linking). Worse, the
  old code re-read and overwrote this value on *every* refresh (i.e. every
  time the Contacts screen opened), so one incidental OS-side touch would
  permanently make a years-old contact look freshly added from then on.
  Fixed to stamp `rawTimestampAdded` once, the first time a contact is seen
  by the app, and never touch it again afterward. Renamed the misleading
  `SystemContact.timesContacted` field to `lastUpdatedTimestamp`.
  Caveat: Android's ContactsContract has no public, reliable "date contact
  was created" field for pre-existing contacts, so the *first* import after
  this fix still uses `lastUpdatedTimestamp` as a best-effort initial value
  per contact - it can still be wrong for a contact that happened to be
  touched recently before this fix shipped. From that first import onward,
  though, every genuinely new contact gets stamped the moment TouchBase
  first sees it, so "last week's new contacts" becomes reliable going
  forward.

- **DB versions bumped repeatedly (1->4) with `fallbackToDestructiveMigration()`.**
  Deliberate for now - pre-release app, no installed base worth writing
  real migrations for yet. Revisit before any real release: switch to
  proper `Migration` objects so a schema bump doesn't wipe user data.

- **Scroll flicker, two separate causes.** (1)
  `refreshFromSystemContacts()` re-upserted every contact on every screen
  visit even when nothing changed, and Room's `@Upsert` writes
  unconditionally - that invalidates Room's change tracking for the
  `contacts` table, which tears down the active `PagingSource` mid-session.
  Fixed by only writing rows that are new or actually changed. (2) The
  screen unmounted its `LazyColumn` (swapping to a spinner) on every
  refresh, discarding scroll position every time you navigated back. Fixed
  by keeping the list mounted once it has data; only the true first load
  blocks with a full-screen spinner.

- **Tag creation was unreachable.** `createTag()` existed on the ViewModel
  and repository but no screen ever called it - the "Tag" button only
  toggled *existing* tags, and none could ever be created. Added a "+ New
  tag" chip in the expanded tag row that creates and assigns a tag in one
  step (`createTagAndAssign`).

- **Merge duplicate contacts.** New `dedupe` package: scans for contacts
  Android's own aggregation didn't already combine (usually differing
  display names) but that share a normalized phone number, using
  union-find so a contact matching two different duplicates only appears
  in one group. User reviews and picks which groups to merge; chosen
  approach is full consolidate-and-delete (not the non-destructive
  `AggregationExceptions` link), matched by phone number only. On merge:
  survivor picked by (has a Google account) then (richest data); phone/
  email not already on the survivor are copied over *specifically onto its
  account-linked raw contact* (not just `rawContactIds.first()` - an
  earlier version of this code got that wrong, which would have silently
  copied data onto a non-syncing local raw contact); losing raw contacts
  are deleted; a Google sync is requested afterward via the existing
  `SyncWorker`. Before any deletion, one re-importable vCard 3.0 backup
  (`.vcf`, proper `N`/`FN`, CRLF line endings, text escaping) covering
  every contact in the whole batch is written to external app storage -
  restorable via Contacts app > Import if a merge turns out wrong.
  Known limitation: only name, phone numbers and emails are preserved for
  a removed contact - both in what gets copied onto the survivor and in
  the backup file itself. Other fields (notes, photos, addresses,
  organization) are lost. A real photo/note preservation pass is future
  work.

- **Merge backup made re-importable + single-file.** Rewrote the vCard
  writer to proper vCard 3.0 (N + FN, CRLF line endings, text escaping)
  and moved it out of `mergeGroup()` so ONE file covers the whole batch,
  written before any group's deletes run - not one file per group
  interleaved with each group's own deletes.

- **Merge review: raw-contact targeting bug.** `mergeGroup()` copied
  phone/email data onto `primary.rawContactIds.first()`, which is
  arbitrary query order - if the account-linked raw contact wasn't first,
  copied data landed on a local-only raw contact and would never reach
  Google. Added `ContactCard.preferredRawContactId` (the account-linked
  one when present) and target that instead. Also added TYPE columns to
  copied phone/email rows to match the rest of the codebase's own inserts.

- **Merge screen looked stuck.** `writeBackup()` did synchronous file I/O
  but wasn't suspend/`Dispatchers.IO` like every other call in this
  feature - made it suspend + IO-dispatched (correct regardless, but on
  its own a small text write isn't slow enough to explain a real hang).
  Confirmed hang wasn't a frozen main thread (the spinner kept animating),
  so the real cause is processing many duplicate groups sequentially - each
  merge is a real IPC round-trip to the Contacts provider - with zero
  progress feedback, which is indistinguishable from actually being stuck.
  Added "Merging group X of Y" progress instead of a bare spinner.

- **Merge "has account" check was too broad.** `DuplicateContactsScanner`
  treated ANY non-null `ACCOUNT_TYPE` (Samsung, Exchange, etc.) as
  `hasGoogleAccount`, so a contact linked only to a non-Google account
  could get picked as the merge survivor / preferredRawContactId target,
  meaning copied data would never actually reach Google. Fixed to check
  specifically for `GOOGLE_ACCOUNT_TYPE` ("com.google", exposed from
  AccountsHelper).

- **List/tag rename+delete were missing entirely.** Tags had no rename or
  delete in ContactDao at all - only insert. SavedFilter had delete but no
  update. Added both, plus a new "Manage lists" screen (overflow menu)
  listing both kinds of "list" with rename/delete, and "Manage members"
  for tags. Considered long-press on the filter chips instead, but nested
  clickable/combinedClickable on top of FilterChip's own internal
  clickable is fragile in Compose (the innermost one tends to win the
  gesture) - a dedicated screen is both more robust and more discoverable
  than a hidden gesture. `CreateListScreen` now doubles as the "manage
  members" editor: nav route takes an optional `tagId` arg
  (`create_list?tagId={tagId}`), and when present it pre-checks the tag's
  current members and diffs on submit instead of creating a new tag.

- **Call history was tracked but never shown anywhere.**
  `CallSessionDao.observeHistoryForContact()`/`observeRecentSessions()`
  already existed - CallSessionItem already records outcome, feedback,
  note, and timestamp per call - but no ViewModel/screen ever called
  either query. Added `ContactHistoryScreen` (per-contact, reached via a
  new "History" button on each contact row) using the existing
  `observeHistoryForContact` query. `observeRecentSessions` (all sessions,
  not per-contact) is still unused - a "recent sessions" list is a
  natural follow-up if wanted.

- **"Create list from contacts."** Search + multi-select + name a list.
  This reuses Tags (bulk-create a tag, bulk-assign it to the selected
  contact ids) rather than a second per-contact membership system, since
  "Lists" were already defined as saved filter presets, not per-contact
  data - see the "Saved lists" entry above.
