# Members

The church's member directory: browse, search, add, edit, deactivate, delete and export members. Used by volunteers (read-only), staff (edit) and admins (delete).

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Members screen | VOLUNTEER | `/members` (`frontend/src/router/index.js:18-23`) |
| Browse, search, filter, sort | VOLUNTEER | `GET /api/members` (`src/main/java/io/github/membertracker/infrastructure/MemberController.java:70-75`) |
| Export all members to CSV | STAFF | "Export CSV" button, `GET /api/members/export` (`MemberController.java:136-141`) |
| Export filtered members to CSV | STAFF | same button, `POST /api/members/export` (`MemberController.java:143-159`) |
| Add a member | STAFF | "Add member" button, `POST /api/members` (`MemberController.java:100-105`) |
| Edit a member | STAFF | "Edit" in the row menu, `PUT /api/members/{id}` (`MemberController.java:107-115`) |
| Deactivate / reactivate | STAFF | "Deactivate" or "Reactivate" in the row menu, same `PUT` (`frontend/src/views/MembersView.vue:426-436`) |
| Delete a member | ADMIN | "Delete" in the row menu, `DELETE /api/members/{id}` (`MemberController.java:117-127`) |

Role view of the screen (the buttons are hidden, not disabled, for roles that cannot use them):
- VOLUNTEER sees the whole table, the search and the filters. There is no "Add member" or "Export CSV" button and no actions column (`MembersView.vue:4`, `:116`).
- STAFF also sees "Add member", "Export CSV" and a More menu on every row with "Edit" and "Deactivate" or "Reactivate".
- ADMIN also sees "Delete" (in clay) at the end of the row menu (`MembersView.vue:318-325`).
- The server enforces the same roles; a 403 shows the shared "Access denied" toast and nothing else changes.

## How it works
### Browse members
1. The screen loads the full member list once on open and again after every save, delete or toggle (`MembersView.vue:270-287`).
2. From `md` up a ruled table shows Name (with the email beneath), Phone, Joined, Status and Dues (`MembersView.vue:82-121`); below `md` the same rows are a stacked list (`MembersView.vue:124-139`).
3. Status is a dot plus a word: "Active" in fern or "Inactive" in clay.
4. Dues apply to active members only. An active member shows "Paid up" or "N months behind" ("1 month behind"). An inactive member shows a muted dash (screen readers hear "Not tracked while inactive"): the server stops counting months for them, so the stored number is stale (`MembersView.vue:107-115`, `:311-317`).
5. On a load failure a banner says "The member list did not load. Check your connection and try again." with a "Try again" button (`MembersView.vue:14-19`). With no members: "No members yet. Add the first member." and an "Add member" button for STAFF and above.

### Search and filter
1. Type in the search box: matches name, email or phone, case-insensitive. A term with 3+ digits also matches phone numbers ignoring spaces and dashes (`frontend/src/utils/memberFilters.js:10-14`).
2. Pick Status (All members / Active / Inactive) and Dues (All / Paid up / Behind). "Behind" means active and `consecutiveMonthsMissed > 0`; "Paid up" means active and 0. The Dues filter applies to active members only: an inactive member matches neither, only "All" (`memberFilters.js:20-25`).
3. Set "Joined from" / "Joined to" (inclusive dates; members with no join date drop out once either is set) (`memberFilters.js:27-33`). Below `md` the two dates sit behind a "More filters" button.
4. All filters combine with AND. "Clear filters" appears when any is set; when nothing matches the screen says "No members match these filters." with a "Clear filters" button (`MembersView.vue:63`, `:71-74`).
- Filtering happens in the browser on the loaded list, by design ([../architecture.md](../architecture.md), revisit above ~2,000 members). Filters are not remembered between visits.

### Sort columns
1. From `md` up, click the Name, Joined or Dues header (`MembersView.vue:260-268`). Headers are buttons with `aria-sort`.
2. First click sorts ascending, a second click on the same header flips it (`MembersView.vue:288-294`). Empty values always sort last, and so do inactive members when sorting by Dues (`memberFilters.js:39-60`).
- There is no sort control below `md`.

### Add a member
1. Click "Add member" and fill the name (required), optionally email and phone, and "Joined on" (default today, not in the future); the dialog has no "Active" switch because a new member is always active (`MembersView.vue:143-168`).
2. "Add member" in the dialog sends `{name, email, phone?, joinDate?, active}` to `POST /api/members` (`MembersView.vue:387-408`, `frontend/src/utils/memberPayload.js`). The name is checked on the screen first, and the email only when filled (format).
3. The server creates the member with the status MEMBER (or INACTIVE when the request asks for it: `status`, or the legacy `active: false`) and the counters at zero; it uses the join date sent, or today (`src/main/java/io/github/membertracker/usecase/SaveMemberUseCase.java`). The screen still sends `active: true`, so a new member is a MEMBER. DECEASED, TRANSFERRED and ARCHIVED are refused on create (400, field error on `status`).
4. Success: the dialog closes, the row appears and a toast "Member added" names the member. The email may be left empty, and two members may share one address: there is no duplicate check. A child added without an email counts as a member until the status step of the [person/membership plan](../person-membership-plan.md): they appear behind on dues unless marked inactive. The dialog's hint says so.
5. Other failures (validation, 403): field errors from the server show under their fields; anything else shows in a banner at the top of the dialog plus an error toast "Could not save member" (no toast for 403); the dialog stays open (`MembersView.vue:369-386`).

### Edit a member
1. Choose "Edit" in the row menu; the dialog is pre-filled from the row (`MembersView.vue:342-354`) (STAFF+).
2. "Save changes" sends the form to `PUT /api/members/{id}` as a `MemberRequest` (`MemberController.java:107-115`). The server loads the stored member and applies name, email, phone, join date (when sent) and `active`; the missed-months counter, last payment date and monthly-job marker are never taken from the client (`src/main/java/io/github/membertracker/usecase/UpdateMemberUseCase.java:25-60`).
3. Unknown id returns an empty 404 (`MemberController.java:114`). Any email is accepted, including one another member has, or none. Otherwise the dialog closes, the table reloads and a toast "Member saved" appears.
- The "Active" switch is shown only when editing a member (`v-if="editingMember"`, `MembersView.vue:150`), and is honoured there; changing it goes through `Member.activate()` / `deactivate()`. Its help text says inactive members do not count as behind and that turning it back on resets the months behind.

### Deactivate or reactivate
1. Choose "Deactivate" or "Reactivate" in the row menu (STAFF+). There is no dialog: the change is easy to undo, so it acts at once.
2. The screen sends the member's name, email, phone, join date and `active` flipped as a `MemberRequest` (`MembersView.vue:426-436`). Toast "Member deactivated" or "Member reactivated"; on failure "Could not deactivate member" or "Could not reactivate member".
- Reactivating goes through `Member.activate()`, which resets the overdue counter to 0 (`Member.java:75-81`, called from `UpdateMemberUseCase.java:41-49`). Deactivating calls `deactivate()`; sending the state the member already has changes nothing.
- Automatic deactivation after 3 missed months does not exist (see Rules).

### Delete a member
1. Choose "Delete" in the row menu (ADMIN only). A dialog "Delete <name>?" says "This permanently deletes the member together with their payments and message history. This cannot be undone." (`MembersView.vue:171-180`).
2. "Delete member" sends `DELETE /api/members/{id}` (`MembersView.vue:409-425`); unknown id returns 404 (`MemberController.java:125`).
3. Permanently lost with the member: every payment and every message delivery record (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`); the dialog says so.
4. Success: a toast "Member deleted". Failure (403 for a non-admin): a banner in the dialog and the toast "Could not delete member" (not for 403), and the member stays.

### Export to CSV
1. Click "Export CSV" (STAFF+). If the filters leave zero rows, a "Nothing to export" toast shows and no request is made (`MembersView.vue:445-454`).
2. With no filter (or a filter matching everyone) the screen calls `GET /api/members/export`; with a narrowed list it calls `POST /api/members/export` with the visible ids (`memberFilters.js:62-67`, `frontend/src/services/api.js:358-363`).
3. The file downloads as `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set (`MembersView.vue:459-460`).
4. Columns: id, name, email (empty when the member has none), phone, joinDate, active, consecutiveMonthsMissed (`MemberController.java:161-174`). The file is UTF-8 with a byte order mark so Excel reads non-Latin names correctly. Cells starting with a formula character are neutralised ([member-controller.md](member-controller.md)).
5. Failure: error toast "Export failed" (`MembersView.vue:461-469`).

## Fields
Stored in the `member` table (migrations `001`, `005`, `009`, `010`); the JSON of a member carries the same names.

| Field | Meaning | Who sets it |
|-------|---------|-------------|
| `id` | Row id, never changes | system |
| `name` | Required, max 100 | `MemberRequest` |
| `email` | Optional, not unique, may be shared | `MemberRequest` |
| `phone` | Optional | `MemberRequest` |
| `joinDate` | Today unless sent | `MemberRequest` |
| `status` | `MEMBER`, `INACTIVE`, `DECEASED`, `TRANSFERRED` or `ARCHIVED` (migration `010`). Only `MEMBER` counts for dues, reminders, messages and payments. The backfill turned every `active = false` into `INACTIVE`; re-label deceased or transferred people by hand | `MemberRequest.status`, or the legacy `active` |
| `active` | Legacy on/off, read-only in the JSON: true only when `status` is `MEMBER`. The `active` column is kept equal to it by `MemberPersistenceMapper` until the contract step | derived |
| `archivedAt` | Null; set by the archive step (not built yet) | system |
| `consecutiveMonthsMissed`, `lastPaymentDate`, `lastMissedCountMonth` | Dues counters | system only |

Changing `status` through `PUT`: see [member-controller.md](member-controller.md). The screens still show only Active and Inactive, driven by `active`; a person marked DECEASED or TRANSFERRED through the API appears as Inactive there until the frontend speaks `status`.

## Rules
- Request shape `MemberRequest` (`src/main/java/io/github/membertracker/infrastructure/dto/MemberRequest.java:15`): name required (max 100), email optional (trimmed, blank becomes null) and well-formed when present (max 100), phone optional (blank becomes null) but if present must match `^\+?[0-9\s\-\(\)]{10,}$`, join date optional and not in the future, `active` optional. Failures return 400 with a field list. `id`, counters, last payment date and the monthly-job marker are not part of it and are ignored if sent.
- Email is optional and not unique. Migration `009.make-member-email-optional.sql` dropped the unique index, made the column nullable and added a plain lookup index `idx_member_email_lookup`. Messages and reminders reach only members with an email, and members sharing an address get one message between them (the one with the lowest id; the others have no delivery row for that message): see [communications.md](communications.md). The activity log never records an email.
- A child added without an email counts as a member until the status step of the [person/membership plan](../person-membership-plan.md): they appear behind on dues unless marked inactive.
- New members: join date defaults to today, status MEMBER unless INACTIVE is asked for, counters zero (`SaveMemberUseCase.java`).
- Export ids: not empty, at most 5000, each positive (`src/main/java/io/github/membertracker/infrastructure/dto/ExportMembersRequest.java:12-13`); unknown ids are skipped (`MemberController.java:147-150`).
- `consecutiveMonthsMissed`, `lastPaymentDate` and `lastMissedCountMonth` are system-managed (never client-settable since audit C8):
  - A recorded payment sets `lastPaymentDate` to the later of the two dates, and resets the counter to 0 only if the payment's period is the current month (`Member.java:46-59`, called from `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java:57`).
  - The monthly scheduler (1st, 06:00) adds 1 to the counter of active members with no payment for the previous month, once per member per month, through `Member.markMissedFor` and `lastMissedCountMonth` (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java:34-49`, `Member.java:66-73`); see [payment-reminder-scheduler.md](payment-reminder-scheduler.md).
- "Behind" and "paid up" apply to active members only. An inactive member's counter is no longer raised or shown: the screens show a dash, the Dues filter and sort skip them, and the Overview, the overdue endpoints and the "behind on dues" messages leave them out.
- Automatic deactivation: there is none. A membership policy (`shouldDeactivate`: 3 or more missed months) once existed but was only reachable through an unused use case; both were removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history. Decide whether to build it for real.

## Known issues
- A member without an email counts for dues until the status step ships (see Rules). A single-member message to one without an email is refused with a 400 (audit C10 is closed by migration 009).
- Automatic deactivation never ran: the pre-due reminder window and automatic deactivation never ran; the code was removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history; decide whether to build them for real.
- Load errors are only logged to the console and shown as the banner; there is no retry other than the "Try again" button.
- A filter that matches every member exports through the full-list endpoint but the file is still named `members_filtered_...` (`MembersView.vue:459`).
- After deleting a row from its menu, focus has nowhere to return (the trigger is gone) and falls to the page.

Fixed since the first version of this page: Delete's dialog now says that payments and message history go with the member (audit C9); the action buttons are hidden for roles that cannot use them instead of failing with a 403.

## Related
- [member-controller.md](member-controller.md): endpoints, errors, CSV details
- [members-view.md](members-view.md): screen state, filter and sort internals
- [payment-controller.md](payment-controller.md), [payments-view.md](payments-view.md): payments that update the counters
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md): counter job
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
