# MembersView

`frontend/src/views/MembersView.vue`

Members screen ("Members": "Everyone on the register: who is paid up and who is behind."): searchable, filterable, sortable ruled table with add, edit, delete, deactivate/reactivate and CSV export. Route `/members`, minimum role VOLUNTEER (`frontend/src/router/index.js`; guards in [../authentication.md](../authentication.md)). Built on Tailwind and the shared components (`PageHead`, `StatusLabel`, `ActionMenu`, `BaseModal`, `BaseInput`, `BaseButton`, `AlertBanner`, `EmptyNote`, `TextButton`); no Bootstrap classes or JavaScript.

## Who sees what
| Role | Sees |
|------|------|
| VOLUNTEER | Read-only list, filters, no "Add member" / "Export CSV" buttons, no actions column |
| STAFF | Plus "Add member", "Export CSV", and a More menu per row: Edit, Deactivate/Reactivate |
| ADMIN | Plus Delete in the row menu |

Gating uses `authStore.isStaff` and `authStore.isAdmin`; the backend enforces the same roles (403 otherwise).

## State
Options API component; local `data()`, not the Pinia store.

| Field | Meaning |
|-------|---------|
| `members` | Full list from `GET /members`; `[]` on load error |
| `loaded`, `loadError` | First load finished; the last load failed (shows an `AlertBanner` with "Try again") |
| `filters` | `search`, `status` (ALL/ACTIVE/INACTIVE), `paymentStatus` (ALL/CURRENT/OVERDUE, shown as "Dues": All / Paid up / Behind), `joinedFrom`, `joinedTo` |
| `datesOpen` | Below md the date pair sits under a "More filters" disclosure (`aria-expanded`); from md up it is always visible |
| `sort` | `{key, direction}`; `key` null = server order |
| `memberForm`, `editingMember`, `formOpen`, `saving`, `formError`, `formErrors` | Add/edit dialog: `name`, `email`, `phone`, `joinDate`, `active`; `editingMember` null = add mode; `formErrors` holds per-field messages, `formError` the banner message |
| `selectedMember`, `deleteOpen`, `deleting`, `deleteError` | Delete dialog |
| `filteredMembers` | `filterMembers` then `sortMembers` |
| `hasActiveFilters` | Any filter differs from its default; shows "Clear filters" |

## Client-side filter and sort
All in `frontend/src/utils/memberFilters.js`; all filters are ANDed.

| Aspect | Behaviour |
|--------|-----------|
| Search | Trimmed, case-insensitive substring on `name`, `email`, `phone`; also digits-only match on phone when the term has 3+ digits |
| Status | ACTIVE = `active`, INACTIVE = `!active` |
| Dues | Applies to ACTIVE members only. "Behind" = active and `consecutiveMonthsMissed > 0`; "Paid up" = active and `=== 0`. An inactive member never matches either (it still matches "All"): the server stops counting months for inactive members, so the stored number is stale |
| Join date | Inclusive `YYYY-MM-DD` string compare on `joinDate`; members without a join date are excluded once either bound is set |
| Sort keys | `name` (locale, case-insensitive), `joinDate`, `consecutiveMonthsMissed` (the "Dues" column). Empty values sort last in both directions; inactive members also sort last by dues in both directions |
| Sort toggle | Same key flips asc/desc; new key starts asc. Headers are buttons with `aria-sort` and a caret |

## Table and list
- From md up: a `<table>` with columns Name (name with the email beneath), Phone, Joined, Status (`StatusLabel`: dot plus "Active" in fern or "Inactive" in clay), Dues and, for STAFF+, a final actions cell.
- Dues: "Paid up" (fern text) or "N months behind" (ochre text, `monthsBehind`, so "1 month behind"). For an INACTIVE member the cell shows a muted en dash (screen readers get "Not tracked while inactive"); no "Paid up" or "N months behind".
- Below md the table becomes a stacked list of the same rows (name, email, status, dues, phone and join date, More menu). For an inactive member the dues word is left out there.
- Dates use `formatDate(date, 'MMM d, yyyy')`; a `YYYY-MM-DD` string is read as a local day, so it never shifts by a day west of UTC (`frontend/src/utils/index.js`).
- No sort control exists below md (the headers are gone with the table).

## Actions
- `loadMembers` -> `api.getMembers()`, sets `members`, sets `loadError` on failure. Runs in `created`, after every save, delete or toggle, and from "Try again".
- Row menu (`ActionMenu`, trigger label "More actions for <name>"): Edit, Deactivate or Reactivate, Delete (ADMIN only, in clay).
- `saveMember`: client checks (name required, email required and valid via `isValidEmail`) show under the fields without a request. Then `buildMemberRequest(form)` (`frontend/src/utils/memberPayload.js`, sends `joinDate` when set) and `api.updateMember(id, request)` or `api.createMember(request)`, reload, close the dialog, toast "Member saved" / "Member added". The primary button shows "Saving..." and is disabled while the request runs.
  - Add mode defaults "Joined on" to today (local date, `max` today); edit mode shows the stored date.
  - Failure keeps the dialog open: each `error.fieldErrors` entry (`{field, message}`, set by the API interceptor) goes under the matching field (`name`, `email`, `phone`, `joinDate`); a plain 400 whose message says the email already exists ("A member with this email already exists") goes under Email; anything else goes in an `AlertBanner` at the top of the dialog plus an error toast "Could not save member" (no toast for 403: the shared handler already shows "Access denied").
- Delete: dialog "Delete <name>?" ("This permanently deletes the member together with their payments and message history. This cannot be undone."), buttons "Delete member" (danger) and "Cancel". Success: reload, close, toast "Member deleted". Failure: banner in the dialog and toast "Could not delete member" (not for 403).
- `toggleStatus`: no dialog. `api.updateMember(id, buildMemberRequest({...member, active: !active}))`, reload, toast "Member deactivated" / "Member reactivated". Failure toast "Could not deactivate member" / "Could not reactivate member". Reactivating resets the months behind (the dialog's Active switch says so).
- `clearFilters` resets `filters`; filters are not persisted.
- `exportMembers` (STAFF+ only):
  1. Nothing on screen: warning toast "Nothing to export", no request.
  2. Otherwise `exportIds(filtered, all)`: returns `[]` when the visible count equals the full count, else the visible ids.
  3. `api.exportMembers(ids)`: no ids -> `GET /members/export`; ids -> `POST /members/export {ids}`.
  4. Download via `downloadBlob`. File name `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set.

## Empty and error states
- No members at all: "No members yet. Add the first member." with an "Add member" button (STAFF+).
- Filters match nothing: "No members match these filters." with a "Clear filters" button.
- Load failure: banner "The member list did not load. Check your connection and try again." with "Try again".

## Collaborators
- `api.getMembers`, `createMember`, `updateMember`, `deleteMember`, `exportMembers`: `frontend/src/services/api.js`. Backend: [member-controller.md](member-controller.md).
- `useAppStore().addNotification` for toasts; `useAuthStore` for role gates.
- `ActionMenu`, `BaseModal`, `PageHead`, `StatusLabel`, ... in `frontend/src/components/` (see [../design.md](../design.md)).
- `formatDate`, `localISODate`, `downloadBlob`, `isValidEmail`: `frontend/src/utils/index.js`; `monthsBehind`: `frontend/src/utils/dashboardMeter.js`.
- `memberStore` (`frontend/src/stores/memberStore.js`): not used by this view (only re-exported at `frontend/src/stores/index.js`). Its filters read `firstName`, `lastName`, `status`, `paymentStatus`, none of which `Member` has; it would not work against the current API.

## Errors
- Load failure: banner with "Try again" (also `console.error`).
- Save, delete and toggle failures: see Actions. Typical: 400 "A member with this email already exists", 400 field validation, 409 "The request conflicts with existing data". A 403 shows only the shared "Access denied" toast.
- Export failure: error toast "Export failed" with `error.message`.
- Backend statuses (400 validation, 403 role, 404): see [member-controller.md](member-controller.md).

## Side effects
- Triggers a browser file download on export.
- No localStorage writes.

## Gotchas
- `exportIds` returns `[]` when a filter matches every member, so that case does a full GET export, yet the file is still named `members_filtered_...`.
- POST export is capped at 5000 ids (400 beyond that).
- After deleting a row from its menu, focus has nowhere to return (the trigger is gone) and falls to the page.
