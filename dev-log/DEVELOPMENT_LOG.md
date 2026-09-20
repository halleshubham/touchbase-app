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
