# MembersView

`frontend/src/views/MembersView.vue`

Members screen ("Members": "Everyone on the register: who is paid up and who is behind."): searchable, filterable, sortable ruled table with add, edit, archive, deactivate/reactivate and CSV export. Route `/members`, minimum role VOLUNTEER (`frontend/src/router/index.js`; guards in [../authentication.md](../authentication.md)). Built on Tailwind and the shared components (`PageHead`, `StatusLabel`, `ActionMenu`, `BaseModal`, `BaseInput`, `BaseButton`, `AlertBanner`, `EmptyNote`, `TextButton`); no Bootstrap classes or JavaScript.

## Who sees what
| Role | Sees |
|------|------|
| VOLUNTEER | Read-only list, filters, no "Add member" / "Export CSV" buttons, no actions column |
| STAFF | Plus "Add member", "Export CSV", and a More menu per row: Edit, Mark inactive/Mark active, Change status... |
| ADMIN | Plus Archive in the row menu |

Gating uses `authStore.isStaff` and `authStore.isAdmin`; the backend enforces the same roles (403 otherwise).

## State
Options API component; local `data()`, not the Pinia store.

| Field | Meaning |
|-------|---------|
| `members` | Full list from `GET /members`; `[]` on load error |
| `loaded`, `loadError` | First load finished; the last load failed (shows an `AlertBanner` with "Try again") |
| `filters` | `search`, `status` (ALL, MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED), `paymentStatus` (ALL/CURRENT/OVERDUE, shown as "Dues": All / Paid up / Behind), `joinedFrom`, `joinedTo` |
| `datesOpen` | Below md the date pair sits under a "More filters" disclosure (`aria-expanded`); from md up it is always visible |
| `sort` | `{key, direction}`; `key` null = server order |
| `memberForm`, `editingMember`, `formOpen`, `saving`, `formError`, `formErrors` | Add/edit dialog: `name`, `email`, `phone`, `joinDate`, `status`; `editingMember` null = add mode (the Status select then offers only Member and Inactive, as the server enforces); `formErrors` holds per-field messages, `formError` the banner message |
| `selectedMember`, `deleteOpen`, `deleting`, `deleteError` | Archive dialog (the `delete*` names are kept) |
| `filteredMembers` | `filterMembers` then `sortMembers` |
| `hasActiveFilters` | Any filter differs from its default; shows "Clear filters" |

## Client-side filter and sort
All in `frontend/src/utils/memberFilters.js`; all filters are ANDed.

| Aspect | Behaviour |
|--------|-----------|
| Search | Trimmed, case-insensitive substring on `name`, `email`, `phone`; also digits-only match on phone when the term has 3+ digits |
| Status | ALL = every listed member; any other value equals `member.status`. ARCHIVED (ADMIN only) switches the list to `archivedMembers`, loaded from `GET /api/members?archived=true` |
| Dues | Applies to members with status MEMBER only (`countsForDues`). "Behind" = MEMBER and `consecutiveMonthsMissed > 0`; "Paid up" = MEMBER and `=== 0`. Any other status never matches either (it still matches "All"): the server stops counting months for them, so the stored number is stale |
| Join date | Inclusive `YYYY-MM-DD` string compare on `joinDate`; members without a join date are excluded once either bound is set |
| Sort keys | `name` (locale, case-insensitive), `joinDate`, `consecutiveMonthsMissed` (the "Dues" column). Empty values sort last in both directions; members who are not MEMBER also sort last by dues in both directions |
| Sort toggle | Same key flips asc/desc; new key starts asc. Headers are buttons with `aria-sort` and a caret |

## Table and list
- From md up: a `<table>` with columns Name (name with the email beneath), Phone, Joined, Status (`StatusLabel` from `status`: "Member" fern, "Inactive" clay, "Deceased", "Transferred", "Archived" muted), Dues and, for STAFF+, a final actions cell.
- Dues: "Paid up" (fern text) or "N months behind" (ochre text, `monthsBehind`, so "1 month behind"). For any other status the cell shows a muted en dash (screen readers get "Not tracked while inactive", "while deceased" ...); no "Paid up" or "N months behind".
- Below md the table becomes a list of cards (see Year strip and phone cards). For a member who is not MEMBER the dues word is left out there.
- Dates use `formatDate(date, 'MMM d, yyyy')`; a `YYYY-MM-DD` string is read as a local day, so it never shifts by a day west of UTC (`frontend/src/utils/index.js`).
- No sort control exists below md (the headers are gone with the table).
- A member with a household shows its name under the name (house icon, `householdName`); the add and edit dialog has a "Household" select that sends `householdId` only once touched (see [households](households.md#screen)).

## Year strip and phone cards
- Data: `api.getPayments()` (`GET /api/payments`, VOLUNTEER+) loads in parallel with the members and after every reload; `paidMonthsByMember` groups `payment.period` ("yyyy-MM") by `payment.member.id`. No backend change. If that call fails the strip cells show a muted dash and the rest of the screen works. It returns every payment, which is fine at this size; a per-member endpoint is the next step if the list grows large.
- Desktop: a column "Nov to Oct, one square a month" (the range follows the current month) with the compact `YearStrip`, before Dues. Sorting and filters are unchanged (the column is not sortable).
- Rule: see [../design.md](../design.md#year-strip) and the header of `frontend/src/utils/yearStrip.js`. Red squares are capped at the server's `consecutiveMonthsMissed`, so the strip and "N months behind" cannot disagree.
- Below md each member is a card: name, email, household, status, dues text, phone and join date, the More menu, the large strip with month initials, then Call (`tel:`, shown with a phone) and Record payment (STAFF+, members who owe dues; links to `/payments?memberId=<id>`, which opens the dialog with that member chosen), both 44px high.

## Actions
- `loadMembers` -> `api.getMembers()`, sets `members`, sets `loadError` on failure. Runs in `created`, after every save, archive or toggle, and from "Try again".
- Row menu (`ActionMenu`, trigger label "More actions for <name>"): Edit, Mark inactive (Member) or Mark active (Inactive), Change status... (opens Edit with the Status select focused), Archive (ADMIN only, in clay). An archived row (ADMIN only) has "Restore" alone.
- `saveMember`: client checks (name required; the email is optional and checked with `isValidEmail` only when filled) show under the fields without a request. Then `buildMemberRequest(form)` (`frontend/src/utils/memberPayload.js`, sends `joinDate` when set) and `api.updateMember(id, request)` or `api.createMember(request)`, reload, close the dialog, toast "Member saved" / "Member added". The primary button shows "Saving..." and is disabled while the request runs.
  - Add mode defaults "Joined on" to today (local date, `max` today); edit mode shows the stored date.
  - Failure keeps the dialog open: each `error.fieldErrors` entry (`{field, message}`, set by the API interceptor) goes under the matching field (`name`, `email`, `phone`, `joinDate`); a field-less 400 and anything else goes in an `AlertBanner` at the top of the dialog plus an error toast "Could not save member" (no toast for 403: the shared handler already shows "Access denied").
- Archive: dialog "Archive <name>?" ("This hides <name> from the lists. Their payments and messages are kept."), buttons "Archive member" (danger) and "Cancel". Success: reload, close, toast "Member archived". Failure: banner in the dialog and toast "Could not archive member" (not for 403). `api.deleteMember` calls `DELETE /api/members/{id}`, which archives.
- `toggleStatus`: no dialog. `api.updateMember(id, buildMemberRequest({...member, status: INACTIVE or MEMBER}))`, reload, toast "Member deactivated" / "Member reactivated". Failure toast "Could not deactivate member" / "Could not reactivate member". Reactivating resets the months behind.
- `restoreMember` (ADMIN, archived rows): `updateMember` with `status: MEMBER`, reload both lists, toast "Member restored" / "Could not restore member".
- `clearFilters` resets `filters`; filters are not persisted.
- `exportMembers` (STAFF+ only):
  1. Nothing on screen: warning toast "Nothing to export", no request.
  2. Otherwise `exportIds(filtered, all)`: returns `[]` when the visible count equals the full count, else the visible ids.
  3. `api.exportMembers(ids)`: no ids -> `GET /members/export`; ids -> `POST /members/export {ids}`.
  4. The file has the header row `ID,Name,Email,Phone,Join date,Months behind,Status` (no `active` column since the contract step; the screen never read it). Download via `downloadBlob`. File name `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set.

## Empty and error states
- No members at all: "No members yet. Add the first member." with an "Add member" button (STAFF+).
- Filters match nothing: "No members match these filters." with a "Clear filters" button.
- Load failure: banner "The member list did not load. Check your connection and try again." with "Try again".

## Collaborators
- `api.getMembers`, `createMember`, `updateMember`, `deleteMember`, `exportMembers`: `frontend/src/services/api.js`. Backend: [member-controller.md](member-controller.md).
- `useAppStore().addNotification` for toasts; `useAuthStore` for role gates.
- `ActionMenu`, `BaseModal`, `PageHead`, `StatusLabel`, ... in `frontend/src/components/` (see [../design.md](../design.md)).
- `formatDate`, `localISODate`, `downloadBlob`, `isValidEmail`: `frontend/src/utils/index.js`; `monthsBehind`: `frontend/src/utils/dashboardMeter.js`.
- `memberStore` (an unused, non-working store) was removed in `chore(ui): remove dead frontend code`.

## Errors
- Load failure: banner with "Try again" (also `console.error`).
- Save, archive and toggle failures: see Actions. Typical: 400 field validation, 409 "The request conflicts with existing data". A 403 shows only the shared "Access denied" toast.
- Export failure: error toast "Export failed" with `error.message`.
- Backend statuses (400 validation, 403 role, 404): see [member-controller.md](member-controller.md).

## Side effects
- Triggers a browser file download on export.
- No localStorage writes.

## Gotchas
- `exportIds` returns `[]` when a filter matches every member, so that case does a full GET export, yet the file is still named `members_filtered_...`.
- POST export is capped at 5000 ids (400 beyond that).
- After archiving a row from its menu, focus has nowhere to return (the trigger is gone) and falls to the page.
