# Members

The church's member directory: browse, search, add, edit, deactivate, delete and export members. Used by volunteers (read-only), staff (edit) and admins (delete).

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Members screen | VOLUNTEER | `/members` (`frontend/src/router/index.js:18-22`) |
| Browse, search, filter, sort | VOLUNTEER | `GET /api/members` (`MemberController.java:70-71`) |
| Export all members to CSV | VOLUNTEER | `GET /api/members/export` (`MemberController.java:132-133`) |
| Export filtered members to CSV | VOLUNTEER | `POST /api/members/export` (`MemberController.java:139-140`) |
| Add a member | STAFF | `POST /api/members` (`MemberController.java:100-101`) |
| Edit a member | STAFF | `PUT /api/members/{id}` (`MemberController.java:107-108`) |
| Activate / deactivate | STAFF | same `PUT` (`MembersView.vue:289-297`) |
| Delete a member | ADMIN | `DELETE /api/members/{id}` (`MemberController.java:117-118`) |

Role view of the screen:
- VOLUNTEER sees the whole table, filters and Export CSV. Add, edit, delete and activate/deactivate buttons are shown to everyone (`MembersView.vue:9`, `:95-103`); a VOLUNTEER who clicks one gets an "Access Denied" toast from the shared 403 handling (`frontend/src/services/api.js:197-224`). The Add/Edit modal stays open and the table is unchanged.
- STAFF can add, edit and toggle status; Delete fails with 403.
- ADMIN can do everything.

## How it works
### Browse members
1. The screen loads the full member list once on open (`MembersView.vue:222`, `:225`) and again after every save, delete or toggle.
2. The table shows name, email, phone, join date, an Active/Inactive badge and a payment badge ("Current" or "N months overdue") (`MembersView.vue:78-92`).
3. On a load failure the table is empty and nothing is shown to the user (`MembersView.vue:229-233`).

### Search and filter
1. Type in the search box: matches name, email or phone, case-insensitive. A term with 3+ digits also matches phone numbers ignoring spaces and dashes (`frontend/src/utils/memberFilters.js:10-14`).
2. Pick Status (All/Active/Inactive) and Payment Status (All/Current/Overdue). Overdue means `consecutiveMonthsMissed > 0` (`memberFilters.js:16-22`).
3. Set "Joined from" / "Joined to" (inclusive dates; members with no join date drop out once either is set) (`memberFilters.js:24-30`).
4. All filters combine with AND. "Clear filters" appears when any is set (`MembersView.vue:46`, `:243`).
- Filtering happens in the browser on the loaded list, by design ([../architecture.md](../architecture.md), revisit above ~2,000 members). Filters are not remembered between visits.

### Sort columns
1. Click the Name, Join Date or "Last Payment" header (`MembersView.vue:62`, `:67`, `:71`).
2. First click sorts ascending, a second click on the same header flips it (`MembersView.vue:236-242`). Empty values always sort last (`memberFilters.js:47-49`).
- "Last Payment" actually sorts by months overdue, not by a date.

### Add a member
1. Click Add Member and fill name and email (required) and optionally phone (`MembersView.vue:125`, `:129`, `:133`) (STAFF+).
2. Save sends `{name, email, phone?, active}` to `POST /api/members` (`MembersView.vue:265-279`, `frontend/src/utils/memberPayload.js`).
3. The server fills the join date with today, forces the member to active and starts the counters at zero (`SaveMemberUseCase.java:18-26`). The form cannot set a join date.
4. Success: modal closes and the new row appears. A duplicate email (any letter case) returns 400 "A member with this email already exists". Failure (validation, duplicate email, 403): the modal stays open and an error toast shows the server's message (`MembersView.vue:275-277`); a 403 shows the shared "Access Denied" toast.

### Edit a member
1. Click the pencil on a row; the form is pre-filled from the row (`MembersView.vue:256-260`) (STAFF+).
2. Save sends the form to `PUT /api/members/{id}` as a `MemberRequest` (`MembersView.vue:265-279`, `MemberController.java:107-114`). The server loads the stored member and applies only name, email, phone, join date (when sent) and `active`; the missed-months counter, last payment date and monthly-job marker are never taken from the client (`UpdateMemberUseCase.java:22-47`).
3. Unknown id returns an empty 404 (`MemberController.java:114`). An email that belongs to another member returns 400 "A member with this email already exists". Otherwise the modal closes and the table reloads.
- The "Active Member" checkbox in the form is honoured on edit; changing it goes through `Member.activate()`/`deactivate()`.

### Activate or deactivate
1. Click the status button on a row (amber to deactivate, green to activate) (`MembersView.vue:101-103`) (STAFF+).
2. The screen sends the member's name, email, phone, join date and `active` flipped as a `MemberRequest` (`MembersView.vue:289-297`). No confirmation, no message on success beyond the badge changing; on failure an error toast shows the server's message.
- Reactivating (inactive to active) goes through `Member.activate()`, which resets the overdue counter to 0 (`Member.java:73-79`, called from `UpdateMemberUseCase.java:39`). Deactivating calls `deactivate()`; sending the state the member already has changes nothing.
- Automatic deactivation after 3 missed months does not exist (see Rules and Known issues).

### Delete a member
1. Click the trash button; a confirmation names the member (`MembersView.vue:160`) (ADMIN only).
2. Confirm sends `DELETE /api/members/{id}` (`MembersView.vue:280-288`); unknown id returns 404 (`MemberController.java:125`).
3. Permanently lost with the member: every payment and every message delivery record (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`). The confirmation text does not say so.
4. A non-admin gets an "Access Denied" toast and the member stays.

### Export to CSV
1. Click Export CSV. If the filters leave zero rows, a "Nothing to export" toast shows and no request is made (`MembersView.vue:323-331`).
2. With no filter (or a filter matching everyone) the screen calls `GET /api/members/export`; with a narrowed list it calls `POST /api/members/export` with the visible ids (`memberFilters.js:59-62`, `frontend/src/services/api.js:374-379`).
3. The file downloads as `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set (`MembersView.vue:336-337`).
4. Columns: id, name, email, phone, joinDate, active, consecutiveMonthsMissed (`MemberController.java:150-163`). The file is UTF-8 with a byte order mark so Excel reads non-Latin names correctly. Cells starting with a formula character are neutralised ([member-controller.md](member-controller.md)).
5. Failure: error toast "Export failed" (`MembersView.vue:338-346`).

## Rules
- Request shape `MemberRequest` (`infrastructure/dto/MemberRequest.java:15`): name required (max 100), email required, well-formed (max 100), phone optional (blank becomes null) but if present must match `^\+?[0-9\s\-\(\)]{10,}$`, join date optional and not in the future, `active` optional. Failures return 400 with a field list. `id`, counters, last payment date and the monthly-job marker are not part of it and are ignored if sent.
- Email is unique, compared ignoring case (`001.schema-creation.sql:19`). A duplicate is a 400 from the use case; if two requests race past that check the database unique index answers 409 "The request conflicts with existing data" (`GlobalExceptionHandler.java:77`).
- New members: join date defaults to today, `active` is forced true, counters zero (`SaveMemberUseCase.java:18-26`).
- Export ids: not empty, at most 5000, each positive (`infrastructure/dto/ExportMembersRequest.java:11-13`); unknown ids are skipped (`MemberController.java:143-147`).
- `consecutiveMonthsMissed`, `lastPaymentDate` and `lastMissedCountMonth` are system-managed (never client-settable since audit C8):
  - A recorded payment sets `lastPaymentDate`, and resets the counter to 0 only if the payment's period is the current month (`Member.java:46-57`, called from `RecordPaymentUseCase.java:40`).
  - The monthly scheduler (1st, 06:00) adds 1 to the counter of active members with no payment for the previous month, once per member per month, through `Member.markMissedFor` and `lastMissedCountMonth` (`UpdateMissingPaymentCountersUseCase.java:34-49`); see [payment-reminder-scheduler.md](payment-reminder-scheduler.md). The unused `Member.markPaymentMissed` was removed in `chore: remove unused domain methods`.
- Automatic deactivation: there is none. A membership policy (`shouldDeactivate`: 3 or more missed months) once existed but was only reachable through an unused use case; both were removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history. Decide whether to build it for real.

## Known issues
- Delete erases payments and delivery history, and the confirmation does not warn (audit C9).
- One email per member is a model limit (audit C10).
- Automatic deactivation never ran: the pre-due reminder window and automatic deactivation never ran; the code was removed in `chore: remove unused use cases, the membership policy and PhoneNumber` and can be recovered from git history; decide whether to build them for real.
- Add/Edit/Delete/toggle buttons show to VOLUNTEERs; the click fails with a 403 toast (`MembersView.vue:95-103`).
- Delete and load errors are only logged to the console (`MembersView.vue:231`, `:286`), apart from the 403 toast. Save and toggle errors show a toast.
- "Last Payment" column shows and sorts months overdue, not the last payment date (`MembersView.vue:71-73`, `:89-91`).
- A filter that matches every member exports through the full-list endpoint but the file is still named `members_filtered_...` (`MembersView.vue:336`).
- Volunteers can export the full congregation (no finer role split; see `../functionality-audit.md` privacy row).

## Related
- [member-controller.md](member-controller.md): endpoints, errors, CSV details
- [members-view.md](members-view.md): screen state, filter and sort internals
- [payment-controller.md](payment-controller.md), [payments-view.md](payments-view.md): payments that update the counters
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md): counter job
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
