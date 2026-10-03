# Members

The church's member directory: browse, search, add, edit, deactivate, delete and export members. Used by volunteers (read-only), staff (edit) and admins (delete).

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Members screen | VOLUNTEER | `/members` (`frontend/src/router/index.js:17-21`) |
| Browse, search, filter, sort | VOLUNTEER | `GET /api/members` (`MemberController.java:66-67`) |
| Export all members to CSV | VOLUNTEER | `GET /api/members/export` (`MemberController.java:134-135`) |
| Export filtered members to CSV | VOLUNTEER | `POST /api/members/export` (`MemberController.java:141-142`) |
| Add a member | STAFF | `POST /api/members` (`MemberController.java:96-97`) |
| Edit a member | STAFF | `PUT /api/members/{id}` (`MemberController.java:103-104`) |
| Activate / deactivate | STAFF | same `PUT` (`MembersView.vue:292-299`) |
| Delete a member | ADMIN | `DELETE /api/members/{id}` (`MemberController.java:115-116`) |

Role view of the screen:
- VOLUNTEER sees the whole table, filters and Export CSV. Add, edit, delete and activate/deactivate buttons are shown to everyone (`MembersView.vue:9`, `:95-103`); a VOLUNTEER who clicks one gets an "Access Denied" toast from the shared 403 handling (`frontend/src/services/api.js:197-224`). The Add/Edit modal stays open and the table is unchanged.
- STAFF can add, edit and toggle status; Delete fails with 403.
- ADMIN can do everything.

## How it works
### Browse members
1. The screen loads the full member list once on open (`MembersView.vue:226`, `:229`) and again after every save, delete or toggle.
2. The table shows name, email, phone, join date, an Active/Inactive badge and a payment badge ("Current" or "N months overdue") (`MembersView.vue:78-92`).
3. On a load failure the table is empty and nothing is shown to the user (`MembersView.vue:233-237`).

### Search and filter
1. Type in the search box: matches name, email or phone, case-insensitive. A term with 3+ digits also matches phone numbers ignoring spaces and dashes (`frontend/src/utils/memberFilters.js:10-14`).
2. Pick Status (All/Active/Inactive) and Payment Status (All/Current/Overdue). Overdue means `consecutiveMonthsMissed > 0` (`memberFilters.js:16-22`).
3. Set "Joined from" / "Joined to" (inclusive dates; members with no join date drop out once either is set) (`memberFilters.js:24-30`).
4. All filters combine with AND. "Clear filters" appears when any is set (`MembersView.vue:46`, `:247`).
- Filtering happens in the browser on the loaded list, by design ([../architecture.md](../architecture.md), revisit above ~2,000 members). Filters are not remembered between visits.

### Sort columns
1. Click the Name, Join Date or "Last Payment" header (`MembersView.vue:62`, `:67`, `:71`).
2. First click sorts ascending, a second click on the same header flips it (`MembersView.vue:240-246`). Empty values always sort last (`memberFilters.js:47-49`).
- "Last Payment" actually sorts by months overdue, not by a date.

### Add a member
1. Click Add Member and fill name, email, phone (the form marks all three required; `MembersView.vue:125`, `:129`, `:133`) (STAFF+).
2. Save sends the form to `POST /api/members` (`MembersView.vue:270-282`).
3. The server fills the join date with today and forces the member to active (`SaveMemberUseCase.java:18-26`). The form cannot set a join date.
4. Success: modal closes and the new row appears. Failure (validation, duplicate email, 403): the modal stays open and the only feedback is the 403 toast or nothing (`MembersView.vue:279-281`).
- The Address field is accepted by the form but never saved (no such field on `Member`).

### Edit a member
1. Click the pencil on a row; the form is pre-filled from the row (`MembersView.vue:261-265`) (STAFF+).
2. Save sends the whole member to `PUT /api/members/{id}` (`MembersView.vue:270-282`, `MemberController.java:103-112`).
3. Unknown id returns an empty 404 (`MemberController.java:112`). Otherwise the modal closes and the table reloads.
- The "Active Member" checkbox in the form is honoured on edit (see Known issues for the system-managed fields that ride along).

### Activate or deactivate
1. Click the status button on a row (amber to deactivate, green to activate) (`MembersView.vue:101-103`) (STAFF+).
2. The screen re-sends the member with `active` flipped (`MembersView.vue:292-299`). No confirmation, no message on success beyond the badge changing.
- Reactivating through this button does not reset the overdue counter (`SaveMemberUseCase` just saves). The domain `Member.activate()` does reset it (`Member.java:63-69`) but is not called from here.
- Automatic deactivation after 3 missed months exists as policy but is never applied (see Rules and Known issues).

### Delete a member
1. Click the trash button; a confirmation names the member (`MembersView.vue:164`) (ADMIN only).
2. Confirm sends `DELETE /api/members/{id}` (`MembersView.vue:283-291`); unknown id returns 404 (`MemberController.java:123`).
3. Permanently lost with the member: every payment and every message delivery record (`src/main/resources/db/sql/001.schema-creation.sql:31`, `:61`). The confirmation text does not say so.
4. A non-admin gets an "Access Denied" toast and the member stays.

### Export to CSV
1. Click Export CSV. If the filters leave zero rows, a "Nothing to export" toast shows and no request is made (`MembersView.vue:315-323`).
2. With no filter (or a filter matching everyone) the screen calls `GET /api/members/export`; with a narrowed list it calls `POST /api/members/export` with the visible ids (`memberFilters.js:59-62`, `frontend/src/services/api.js:374-379`).
3. The file downloads as `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set (`MembersView.vue:328-329`).
4. Columns: id, name, email, phone, joinDate, active, consecutiveMonthsMissed (`MemberController.java:159`). Cells starting with a formula character are neutralised ([member-controller.md](member-controller.md)).
5. Failure: error toast "Export failed" (`MembersView.vue:330-338`).

## Rules
- Name required, email required and well-formed, phone optional but if present must match `^\+?[0-9\s\-\(\)]{10,}$`, join date not in the future (`Member.java:17-28`). Failures return 400 with a field list.
- Email is unique (`001.schema-creation.sql:19`); a duplicate currently surfaces as a 500 (see Known issues).
- New members: join date defaults to today, `active` is forced true (`SaveMemberUseCase.java:18-26`).
- Export ids: not empty, at most 5000, each positive (`infrastructure/dto/ExportMembersRequest.java:11-13`); unknown ids are skipped (`MemberController.java:145-148`).
- `consecutiveMonthsMissed` and `lastPaymentDate` are meant to be system-managed:
  - A recorded payment sets `lastPaymentDate`, and resets the counter to 0 only if the payment's period is the current month (`Member.java:46-57`, called from `RecordPaymentUseCase.java:40`).
  - The daily scheduler adds 1 to the counter for members with no payment for the previous month (`UpdateMissingPaymentCountersUseCase.java:25-33`); see [payment-reminder-scheduler.md](payment-reminder-scheduler.md). `Member.markPaymentMissed` (`Member.java:59-61`) has no caller.
- Automatic deactivation: `DefaultMembershipPolicy.shouldDeactivate` is true for an active member with 3 or more missed months (`DefaultMembershipPolicy.java:17`, `:22-27`). It is applied only inside `ProcessMemberPaymentUseCase` (`ProcessMemberPaymentUseCase.java:67-72`), which no controller calls (bean at `UseCaseConfig.java:145-146`). The payment endpoint uses `RecordPaymentUseCase`, which never checks the policy.

## Known issues
- A client can set `active`, `consecutiveMonthsMissed` and `lastPaymentDate` on create and edit; the screen sends them back on every edit and toggle (audit C8; [member-controller.md](member-controller.md#gotchas), [members-view.md](members-view.md#gotchas)).
- Delete erases payments and delivery history, and the confirmation does not warn (audit C9).
- A duplicate email returns 500 "An unexpected error occurred", not a 400 (`GlobalExceptionHandler.java:101-105` in `infrastructure/handler/`; audit C10 covers the underlying one-email-per-member model). The form shows nothing.
- Automatic deactivation never happens: the policy is only reachable through an unused use case (see Rules).
- The daily counter job is not scheduled today (no `@EnableScheduling`; audit C3), so `consecutiveMonthsMissed` only ever resets, never grows, in practice.
- Add/Edit/Delete/toggle buttons show to VOLUNTEERs; the click fails with a 403 toast (`MembersView.vue:95-103`).
- Save, delete, toggle and load errors are only logged to the console (`MembersView.vue:235`, `:280`, `:289`, `:297`), apart from the 403 toast.
- Address field in the form is ignored by the backend (`MembersView.vue:137`).
- "Last Payment" column shows and sorts months overdue, not the last payment date (`MembersView.vue:71-73`, `:89-91`).
- A filter that matches every member exports through the full-list endpoint but the file is still named `members_filtered_...` (`MembersView.vue:328`).
- Volunteers can export the full congregation (no finer role split; see `../functionality-audit.md` privacy row).

## Related
- [member-controller.md](member-controller.md): endpoints, errors, CSV details
- [members-view.md](members-view.md): screen state, filter and sort internals
- [payment-controller.md](payment-controller.md), [payments-view.md](payments-view.md): payments that update the counters
- [payment-reminder-scheduler.md](payment-reminder-scheduler.md): counter job
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md), [../functionality-audit.md](../functionality-audit.md)
