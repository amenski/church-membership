# Members

The church's member directory: browse, search, add, edit, deactivate, archive and export members, with a page for each member. Used by volunteers (read-only), staff (edit) and admins (archive, restore, delete for good).

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Members screen | VOLUNTEER | `/members` (`frontend/src/router/index.js`) |
| Browse, search, filter, sort | VOLUNTEER | `GET /api/members` (`src/main/java/io/github/membertracker/infrastructure/MemberController.java`) |
| Export all members to CSV | STAFF | "Export CSV" button, `GET /api/members/export` (`MemberController.java`) |
| Export filtered members to CSV | STAFF | same button, `POST /api/members/export` (`MemberController.java`) |
| Add a member | STAFF | "Add member" button, `POST /api/members` (`MemberController.java`) |
| Edit a member | STAFF | "Edit" in the row menu, `PUT /api/members/{id}` (`MemberController.java`) |
| Mark inactive / active, change status | STAFF | "Mark inactive", "Mark active" or "Change status..." in the row menu, same `PUT` with `status` |
| Archive a member | ADMIN | "Archive" in the row menu, `DELETE /api/members/{id}` (archives, nothing is deleted) |
| Restore an archived member | ADMIN | `PUT /api/members/{id}` with `status` MEMBER or INACTIVE ("Restore" on an archived row) |
| Delete a member for good | ADMIN | "Delete for good" on an archived row, `DELETE /api/members/{id}/permanent`: refused (409) when the member has payments or messages |
| Open a member's page | VOLUNTEER | the name links to `/members/:id`: [member-detail-view.md](member-detail-view.md) |
| Send a message, export, mark inactive or archive several members | STAFF (Archive: ADMIN) | tick rows, then the selection bar; see [members-view.md](members-view.md#selection-and-bulk-actions) |

Role view of the screen (the buttons are hidden, not disabled, for roles that cannot use them):
- VOLUNTEER sees the whole table, the search and the filters. There is no "Add member" or "Export CSV" button, no checkboxes and no actions column (`MembersView.vue`).
- STAFF also sees "Add member", "Export CSV", row checkboxes with the selection bar and a More menu on every row with "Edit", "Mark inactive" (a Member) or "Mark active" (an Inactive one) and "Change status..." (opens Edit with the Status select focused).
- ADMIN also sees "Archive" (in clay) at the end of the row menu and in the selection bar, the "Archived" segment of the status control (it loads `GET /api/members?archived=true`) and, on an archived row, "Restore" and "Delete for good".
- The server enforces the same roles; a 403 shows the shared "Access denied" toast and nothing else changes.

## How it works
### Browse members
1. The screen loads the full member list once on open and again after every save, archive or toggle (`MembersView.vue`). It shows 25 rows a page; page and size are in the URL (`?page=2&size=50`).
2. From `lg` up a table in a card shows (STAFF+: a checkbox), Member (the name, which links to the member page, with the email beneath), Household, Status, the year strip, Dues, Last paid and Phone; below `lg` the same rows are stacked cards (`MembersView.vue`).
3. Status is a dot plus a word from `status`: "Member" in fern, "Inactive" in clay, "Deceased", "Transferred" and "Archived" muted (`utils/memberStatus.js`).
4. Dues apply to members with status MEMBER only (`countsForDues`, the one rule for every screen). A Member shows "Paid up" or "N months behind" ("1 month behind"). Every other status shows a muted dash (screen readers hear "Not tracked while inactive", "while deceased" and so on): the server stops counting months for them, so the stored number is stale (`MembersView.vue`).
5. On a load failure a banner says "The member list did not load. Check your connection and try again." with a "Try again" button (`MembersView.vue`). With no members: "No members yet. Add the first member." and an "Add member" button for STAFF and above.

### Search and filter
1. Type in the search box: matches name, email or phone, case-insensitive. A term with 3+ digits also matches phone numbers ignoring spaces and dashes (`frontend/src/utils/memberFilters.js`).
2. Pick a Status segment (All, Member, Inactive, Transferred, Deceased, plus Archived for ADMIN; each shows its count) and Dues (All / Paid up / Behind). "All" is everyone listed (not archived); the others are exactly that status. "Behind" means a Member with `consecutiveMonthsMissed > 0`; "Paid up" means a Member and 0. The Dues filter applies to Members only: any other status matches neither, only "All" (`memberFilters.js`).
3. Set "Joined from" / "Joined to" (inclusive dates; members with no join date drop out once either is set) (`memberFilters.js`). They sit behind a "More filters" popover from `lg` and behind the "Filters" button below it.
4. All filters combine with AND. "Clear filters" appears when any is set; when nothing matches the screen says "No members match these filters." with a "Clear filters" button (`MembersView.vue`).
- Filtering happens in the browser on the loaded list, by design ([../architecture.md](../architecture.md), revisit above ~2,000 members). Filters are not remembered between visits; only the page, the page size and the Dues filter (`?dues=behind|paid|all`, the Overview's "See all" link) ride in the URL.

### Sort columns
1. From `lg` up, click the Member or Dues header (`MembersView.vue`). Headers are buttons with `aria-sort`. The "Sort by" select (Name, Most behind, Joined) works on every width and shares the same sort.
2. First click sorts ascending, a second click on the same header flips it (`MembersView.vue`). Empty values always sort last, and so do members who are not MEMBER when sorting by Dues (`memberFilters.js`).
- Below `lg` there are no headers; the "Sort by" select is the only control.

### Add a member
1. Click "Add member" and fill the name (required), optionally email and phone, and "Joined on" (default today, not in the future); a Status select offers only Member (default) and Inactive, as the server enforces (`MembersView.vue`).
2. "Add member" in the dialog sends `{name, email?, phone?, joinDate?, status}` to `POST /api/members` (`MembersView.vue`, `frontend/src/utils/memberPayload.js`). The name is checked on the screen first, and the email only when filled (format).
3. The server creates the member with the status MEMBER (or INACTIVE when the request asks for it with `status`) and the counters at zero; it uses the join date sent, or today (`src/main/java/io/github/membertracker/usecase/SaveMemberUseCase.java`). The screen sends `status`. DECEASED, TRANSFERRED and ARCHIVED are refused on create (400, field error on `status`).
4. Success: the dialog closes, the row appears and a toast "Member added" names the member. The email may be left empty, and two members may share one address: there is no duplicate check. A child added without an email counts as a member who owes dues unless they are added as Inactive: the dialog's hint says "choose Inactive". Someone who pays no dues at all can be a person without a membership instead: [people.md](people.md).
5. Other failures (validation, 403): field errors from the server show under their fields; anything else shows in a banner at the top of the dialog plus an error toast "Could not save member" (no toast for 403); the dialog stays open (`MembersView.vue`).

### Edit a member
1. Choose "Edit" in the row menu; the dialog is pre-filled from the row (`MembersView.vue`) (STAFF+).
2. "Save changes" sends the form to `PUT /api/members/{id}` as a `MemberRequest` (`MemberController.java`). The server loads the stored member and applies name, email, phone, join date (when sent) and `status`; the missed-months counter, last payment date and monthly-job marker are never taken from the client (`src/main/java/io/github/membertracker/usecase/UpdateMemberUseCase.java`).
3. Unknown id returns an empty 404 (`MemberController.java`). Any email is accepted, including one another member has, or none. Otherwise the dialog closes, the table reloads and a toast "Member saved" appears.
- The Status select offers Member, Inactive, Deceased and Transferred when editing (never Archived: that is the menu action); a change goes through `Member.activate()` / `deactivate()` or sets DECEASED / TRANSFERRED directly. Its hint says only a Member owes dues and gets messages and that moving someone back to Member resets the months behind. "Change status..." in the row menu opens this dialog with the select focused.

### Mark inactive or active
1. Choose "Mark inactive" (a Member) or "Mark active" (an Inactive member) in the row menu (STAFF+); other statuses only have "Change status...". There is no dialog: the change is easy to undo, so it acts at once.
2. The screen sends the member's name, email, phone, join date and `status` INACTIVE or MEMBER as a `MemberRequest`. Toast "Member deactivated" or "Member reactivated"; on failure "Could not deactivate member" or "Could not reactivate member".

### Restore an archived member (ADMIN)
Choose "Archived" in the Status filter, open the row menu and choose "Restore": the screen sends `status` MEMBER (the months behind reset), the member returns to the main list, toast "Member restored"; failure toast "Could not restore member".
- Reactivating goes through `Member.activate()`, which resets the overdue counter to 0 (`Member.java`, called from `UpdateMemberUseCase.java`). Deactivating calls `deactivate()`; sending the state the member already has changes nothing.
- Automatic deactivation after 3 missed months does not exist (see Rules).

### Archive a member
1. Choose "Archive" in the row menu (ADMIN only). A dialog "Archive <name>?" says "This hides <name> from the lists. Their payments and messages are kept." with the buttons "Archive member" and "Cancel".
2. "Archive member" sends `DELETE /api/members/{id}`, which now archives (`ArchiveMemberUseCase`): status ARCHIVED, `archivedAt` set to now, the months-behind counter untouched, the row, the payments and the deliveries kept. Nothing is deleted; an unknown id returns 404; archiving an archived member changes nothing.
3. The member disappears from every list, count and export; an ADMIN can list the archived with `GET /api/members?archived=true`, and restore one by `PUT` with `status` `MEMBER` (counter reset) or `INACTIVE` (counter frozen), which clears `archivedAt`. An archived member cannot be sent to DECEASED or TRANSFERRED directly. Only an ADMIN can see or restore an archived member (see Rules).
4. Success: a toast "Member archived". Failure (403 for a non-admin): a banner in the dialog and the toast "Could not archive member" (not for 403), and the member stays.
5. The activity log records MEMBER_ARCHIVED ("Member X was archived").

### Delete for good (ADMIN, Archived view)
`DELETE /api/members/{id}/permanent` (ADMIN) removes the row only when the member has no payment and no message delivery (a record made by mistake); otherwise it answers 409 `MEMBER_010` and nothing changes. The database enforces the same rule: migration `011` made the payment and delivery foreign keys `ON DELETE RESTRICT`, so even a raw `DELETE FROM member` fails (MySQL error 1451) while history exists. The activity log records MEMBER_DELETED. The screen has a "Delete for good" button on archived rows (ADMIN); it is disabled for a member whose last 12 months already show a payment, and the server's 409 decides the rest.

### Export to CSV
1. Click "Export CSV" (STAFF+). If the filters leave zero rows, a "Nothing to export" toast shows and no request is made (`MembersView.vue`).
2. With no filter (or a filter matching everyone) the screen calls `GET /api/members/export`; with a narrowed list it calls `POST /api/members/export` with the visible ids (`memberFilters.js`, `frontend/src/services/api.js`).
3. The file downloads as `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set (`MembersView.vue`).
4. Columns: ID, Name, Email (empty when the member has none), Phone, Join date, Months behind, Status (`MemberController.java`; plain headers and no `active` column since the contract step). The file is UTF-8 with a byte order mark so Excel reads non-Latin names correctly. Cells starting with a formula character are neutralised ([member-controller.md](member-controller.md)).
5. Failure: error toast "Export failed" (`MembersView.vue`).

## Fields
Stored in the `member` table (migrations `001`, `005`, `009`, `010`; `011` makes payments and deliveries restrict deletes); the JSON of a member carries the same names.

| Field | Meaning | Who sets it |
|-------|---------|-------------|
| `id` | Row id, never changes | system |
| `name` | Required, max 100 | `MemberRequest` |
| `email` | Optional, not unique, may be shared | `MemberRequest` |
| `phone` | Optional | `MemberRequest` |
| `joinDate` | Today unless sent | `MemberRequest` |
| `status` | `MEMBER`, `INACTIVE`, `DECEASED`, `TRANSFERRED` or `ARCHIVED` (migration `010`). Only `MEMBER` counts for dues, reminders, messages and payments. The backfill turned every `active = false` into `INACTIVE`; re-label deceased or transferred people by hand | `MemberRequest.status` |
| `archivedAt` | Null until the member is archived; set to the archive time, cleared on restore | system |
| `householdId`, `householdName` | The household of the person behind the membership, or null; the name is read-only. Set with the optional `householdId` of the request (absent keeps it, null clears it, an unknown id is a 400 `HOUSEHOLD_001`): see [households.md](households.md) | `MemberRequest.householdId` |
| `consecutiveMonthsMissed`, `lastPaymentDate`, `lastMissedCountMonth` | Dues counters | system only |

Changing `status` through `PUT`: see [member-controller.md](member-controller.md). The screens read `status` only. The old `active` flag is gone from the JSON, the request, the CSV and the database (migration `014`); `name`, `email` and `phone` are stored on the linked person (`person` table), not on `member`.

## Rules
- Status rules (the tests of `MemberStatus` are this table). Only MEMBER counts for dues, reminders, messages and payments:

| Status | Dues counter | Reminders | Messages | Listed by default | Payment can be recorded |
|---|---|---|---|---|---|
| MEMBER | yes | yes | yes | yes | yes |
| INACTIVE | frozen; reactivating resets it | no | no | yes | no |
| DECEASED | frozen | no | never | yes | no |
| TRANSFERRED | frozen | no | no | yes | no |
| ARCHIVED | frozen | no | no | no: only `GET /api/members?archived=true` (ADMIN) | no |

- An ARCHIVED member is visible to ADMIN only on every read path (by id, id-based export, payments of the member, send to one member; embedded in payments and deliveries, email and phone are blanked for others): see [../authentication.md](../authentication.md).
- Reads go by status: `findAll` (the list, the full export, the "inactive" list and the Overview total) leaves out ARCHIVED, "dues paying" is status MEMBER, and `findByConsecutiveMonthsMissedGreaterThanEqual` returns MEMBER-status members only.
- Request shape `MemberRequest` (`src/main/java/io/github/membertracker/infrastructure/dto/MemberRequest.java`): name required (max 100), email optional (trimmed, blank becomes null) and well-formed when present (max 100), phone optional (blank becomes null) but if present must match `^\+?[0-9\s\-\(\)]{10,}$`, join date optional and not in the future, `status` optional, `householdId` optional. Failures return 400 with a field list. `id`, counters, last payment date and the monthly-job marker are not part of it and are ignored if sent.
- Email is optional and not unique. Migration `009.make-member-email-optional.sql` dropped the unique index, made the column nullable and added a plain lookup index `idx_member_email_lookup`. Messages and reminders reach only members with an email, and members sharing an address get one message between them (the one with the lowest id; the others have no delivery row for that message): see [communications.md](communications.md). The activity log never records an email.
- A child added without an email is a member like any other: they count for dues and appear behind unless marked inactive. A dependent who pays no dues is a person without a membership ([people.md](people.md)).
- New members: join date defaults to today, status MEMBER unless INACTIVE is asked for, counters zero (`SaveMemberUseCase.java`).
- Export ids: not empty, at most 5000, each positive (`src/main/java/io/github/membertracker/infrastructure/dto/ExportMembersRequest.java`); unknown ids are skipped. The selected export drops archived members for everyone but an ADMIN; the full export leaves them out for all.
- `consecutiveMonthsMissed`, `lastPaymentDate` and `lastMissedCountMonth` are system-managed (never client-settable since audit C8):
  - A recorded payment sets `lastPaymentDate` to the later of the two dates, and resets the counter to 0 only if the payment's period is the current month (`Member.java`, called from `src/main/java/io/github/membertracker/usecase/RecordPaymentUseCase.java`).
  - The monthly scheduler (1st, 06:00) adds 1 to the counter of members with status MEMBER and no payment for the previous month, once per member per month, through `Member.markMissedFor` and `lastMissedCountMonth` (`src/main/java/io/github/membertracker/usecase/UpdateMissingPaymentCountersUseCase.java`, `Member.java`); see [payment-reminder-scheduler.md](payment-reminder-scheduler.md).
- "Behind" and "paid up" apply to members with status MEMBER only. For any other status the counter is frozen and not shown: the screens show a dash, the Dues filter and sort skip them, and the Overview, the overdue endpoints and the "behind on dues" messages leave them out.
- Automatic deactivation: there is none. A membership policy (`shouldDeactivate`: 3 or more missed months) once existed but was only reachable through an unused use case; both were removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history. Decide whether to build it for real.

## Known issues
- A member without an email counts for dues (mark them inactive, or keep a dependent as a person without a membership). A single-member message to one without an email is refused with a 400.
- The pre-due reminder window and automatic deactivation do not exist; the code was removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history. Whether to build them is open in [../todo.md](../todo.md).
- Load errors are only logged to the console and shown as the banner; there is no retry other than the "Try again" button.
- A filter that matches every member exports through the full-list endpoint but the file is still named `members_filtered_...` (`MembersView.vue`).
- After archiving a row from its menu, focus has nowhere to return (the trigger is gone) and falls to the page.

Fixed since the first version of this page: deleting a member no longer erases payments and message history, the member is archived and the database refuses to erase history (audit C9); the action buttons are hidden for roles that cannot use them instead of failing with a 403.

## Related
- [my-dues.md](my-dues.md): a member's own view of their dues, matched to a member by email
- [households.md](households.md): households and the `householdId` of a member
- [member-controller.md](member-controller.md): endpoints, errors, CSV details
- [members-view.md](members-view.md): screen state, filter and sort internals
- [payment-controller.md](payment-controller.md), [payments-view.md](payments-view.md): payments that update the counters
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md): counter job
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
