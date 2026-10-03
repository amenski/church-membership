# MembersView

`frontend/src/views/MembersView.vue`

Members screen: searchable, filterable, sortable table with add, edit, delete, activate/deactivate and CSV export. Route `/members`, minimum role VOLUNTEER (`frontend/src/router/index.js:17-21`; guards in [../authentication.md](../authentication.md)).

## State
Options API component; local `data()`, not the Pinia store.

| Field | Meaning |
|-------|---------|
| `members` | Full list from `GET /members`; `[]` on load error (`MembersView.vue:189`, `:229-233`) |
| `filters` | `search`, `status` (ALL/ACTIVE/INACTIVE), `paymentStatus` (ALL/CURRENT/OVERDUE), `joinedFrom`, `joinedTo` (`:190-196`) |
| `sort` | `{key, direction}`; `key` null = server order (`:197`) |
| `memberForm`, `editingMember` | Modal form with `name`, `email` (required), `phone` (optional) and `active`; `editingMember` null = add mode (`:198-203`) |
| `selectedMember` | Target of the delete modal (`:205`) |
| `filteredMembers` | `filterMembers` then `sortMembers` (`:209-212`) |
| `hasActiveFilters` | Any filter differs from its default (`:213-216`) |

There is no `isLoading` or `error` state. Failures go to `console.error` (see Errors).

## Client-side filter and sort
All in `frontend/src/utils/memberFilters.js`; all filters are ANDed.

| Aspect | Behaviour | Ref |
|--------|-----------|-----|
| Search | Trimmed, case-insensitive substring on `name`, `email`, `phone`; also digits-only match on phone when the term has 3+ digits | `memberFilters.js:6-14` |
| Status | ACTIVE = `active`, INACTIVE = `!active` | `:16-18` |
| Payment status | OVERDUE = `consecutiveMonthsMissed > 0`, CURRENT = `=== 0` | `:20-22` |
| Join date | Inclusive `YYYY-MM-DD` string compare on `joinDate`; members without a join date are excluded once either bound is set | `:24-30` |
| Sort keys | `name` (locale, case-insensitive), `joinDate`, `consecutiveMonthsMissed` (shown as "Last Payment"); empty values sort last in both directions | `:36-55`, headers `MembersView.vue:62`, `:67`, `:71` |
| Sort toggle | Same key flips asc/desc; new key starts asc | `MembersView.vue:236-242` |

## Actions
- `loadMembers` -> `api.getMembers()`, sets `members` (`:225`). Runs in `created` (`:221`) and after every save, delete or toggle.
- `saveMember` -> `buildMemberRequest(form)` (`frontend/src/utils/memberPayload.js`), then `api.updateMember(id, request)` or `api.createMember(request)`, reload, hide modal (`:265-279`). On failure the modal stays open and an error toast shows (see Errors).
- `deleteMember` -> `api.deleteMember(id)`, reload, hide modal (`:280-288`).
- `toggleStatus` -> `api.updateMember(id, buildMemberRequest({...member, active: !active}))`, reload (`:289-297`). Only the `MemberRequest` fields are sent, never the counters or payment dates; the server applies `active` through `Member.activate()`/`deactivate()`.
- `notifyFailure(title, error)` -> error toast with the API error's `message` (the server's `detail`, e.g. "A member with this email already exists"); skipped for 403, which the shared handler already toasts (`:299-307`).
- `clearFilters` resets `filters` (`:243`); filters are not persisted.
- `exportMembers` (`:322-347`):
  1. Nothing on screen (`filteredMembers` empty): warning toast "Nothing to export", no request (`:323-331`).
  2. Otherwise `exportIds(filtered, all)` (`memberFilters.js:59-62`): returns `[]` when the visible count equals the full count, else the visible ids.
  3. `api.exportMembers(ids)`: no ids -> `GET /members/export`; ids -> `POST /members/export {ids}` (`frontend/src/services/api.js:374-379`).
  4. Download via `downloadBlob`. File name `members_<date>.csv`, or `members_filtered_<date>.csv` when any filter is set (`:336-337`).

## Collaborators
- `api.getMembers`, `createMember`, `updateMember`, `deleteMember`, `exportMembers`: `frontend/src/services/api.js:350`, `:358`, `:362`, `:366`, `:374`. Backend: [member-controller.md](member-controller.md).
- `useAppStore().addNotification` for toasts (`frontend/src/stores/appStore.js:67`).
- `downloadBlob`: `frontend/src/utils/index.js:92`.
- `memberStore` (`frontend/src/stores/memberStore.js`): not used by this view (only re-exported at `frontend/src/stores/index.js:3`). Its API: state `members`, `currentMember`, `isLoading`, `error`, `filters`, `pagination`; getters `activeMembers`, `inactiveMembers`, `overdueMembers`, `filteredMembers`, `paginatedMembers`, `memberStats`; actions `loadMembers`, `loadMemberById`, `createMember`, `updateMember`, `deleteMember`, `loadOverdueMembers`, `setFilters`, `setPagination`, `reset` and others. `isLoading` is set around each call, `error` holds the message, and failed actions rethrow.
- Member shape: [member-controller.md](member-controller.md) (`Member`).

## Errors
- Load and delete failures: `console.error` only, no user message (`MembersView.vue:231`, `:286`).
- Save and toggle failures: `console.error` plus an error toast "Could not save member" / "Could not change member status" carrying the server's message (`MembersView.vue:276-277`, `:294-295`). Typical: 400 "A member with this email already exists", 400 field validation, 409 "The request conflicts with existing data". A 403 shows only the shared "Access Denied" toast.
- Export failure: error toast "Export failed" with `error.message` (`:338-346`). Server messages come from the shared API error handling ([../architecture.md](../architecture.md)).
- Backend statuses (400 validation, 403 role, 404): see [member-controller.md](member-controller.md).

## Side effects
- Triggers a browser file download on export.
- Bootstrap modals opened imperatively (`:254`, `:259`, `:263`).
- No localStorage writes.

## Gotchas
- Add, Edit, Delete and toggle buttons render for every role; there is no role check in the template (`MembersView.vue:9`, `:95-103`). Add/Edit/toggle need STAFF+ and Delete needs ADMIN, so a VOLUNTEER gets 403. The shared handler in `frontend/src/services/api.js` shows an "Access Denied" error toast for any 403 (`api.js:198-224`); the component adds no second toast for a 403 (`MembersView.vue:299-301`) and the Add/Edit modal stays open on failure.
- Delete confirmation does not mention that payments and delivery history are erased too (`:160`) (audit C9).
- `exportIds` returns `[]` when a filter matches every member, so that case does a full GET export, yet the file is still named `members_filtered_...` (`memberFilters.js:60`, `MembersView.vue:336`).
- POST export is capped at 5000 ids (400 beyond that) (`ExportMembersRequest.java:12`).
- `memberStore` is unused here and its filters read `firstName`, `lastName`, `status`, `paymentStatus`, none of which `Member` has (`memberStore.js:29`, `:53`, `:61`). It would not work against the current API.
