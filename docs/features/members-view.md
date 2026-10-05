# MembersView

`frontend/src/views/MembersView.vue`

Members screen ("Members", no subtitle): searchable, filterable, sortable table (in a bordered card from lg) with add, edit, archive, deactivate/reactivate, CSV export and a selection bar for bulk actions. Route `/members`, minimum role VOLUNTEER (`frontend/src/router/index.js`; guards in [../authentication.md](../authentication.md)). Built on Tailwind and the shared components (`PageHead`, `StatusBadge`, `StatusLabel`, `ActionMenu`, `BaseModal`, `BaseInput`, `BaseButton`, `AlertBanner`, `EmptyNote`, `TextButton`); no Bootstrap classes or JavaScript.

## Who sees what
| Role | Sees |
|------|------|
| VOLUNTEER | Read-only list, filters, no "Add member" / "Export CSV" buttons (header, or the phone count line), no actions column |
| STAFF | Plus "Add member", "Export CSV", row checkboxes with the selection bar (Send message, Export selected, Mark inactive), and a More menu per row: Edit, Mark inactive/Mark active, Change status... |
| ADMIN | Plus Archive in the row menu and in the selection bar, the "Archived" segment, and the Archived view with Restore and Delete for good |

Gating uses `authStore.isStaff` and `authStore.isAdmin`; the backend enforces the same roles (403 otherwise).

## State
Options API component; local `data()`, not the Pinia store.

| Field | Meaning |
|-------|---------|
| `members` | Full list from `GET /members`; `[]` on load error |
| `loaded`, `loadError` | First load finished; the last load failed (shows an `AlertBanner` with "Try again") |
| `filters` | `search`, `status` (ALL, MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED; set by the segmented control), `paymentStatus` (ALL/CURRENT/OVERDUE, shown as "Dues": All / Paid up / Behind), `joinedFrom`, `joinedTo` |
| `filtersOpen` | Below lg (phone and tablet) Dues, Sort by and the date pair sit under one "Filters" disclosure button (`aria-expanded`, `aria-controls="filter-panel"`), closed by default; from lg up Dues and Sort by are always in the row |
| `moreOpen` | From lg only: the "More filters" popover with Joined from / Joined to is open (`aria-expanded`, `aria-controls="filter-dates"`); Escape closes it and returns focus to the button |
| `dateFilterCount` | How many of the two dates are set; the desktop button reads "More filters (1)" and turns teal when it is above 0 |
| `panelFilterCount` | How many of Dues and the two dates are set; the phone button reads "Filters (2)" and turns teal when it is above 0. Status and search stay in view, so they are not counted; Sort by is not a filter |
| `sort` | `{key, direction}`; starts at `name` ascending. Set by a column header or by the "Sort by" select (see Client-side filter and sort) |
| `selectedIds`, `bulkAction`, `bulkBusy` | The ticked members (by id), which bulk confirm dialog is open (`inactive`, `archive`, or null), and a bulk run in progress. See Selection and bulk actions |
| `formOpen`, `editingMember`, `focusStatus` | Add/edit dialog, which is `MemberFormDialog` (`frontend/src/components/MemberFormDialog.vue`, shared with [the member page](member-detail-view.md)): it owns the form, validation, household list and save; `editingMember` null = add mode (the Status select then offers only Member and Inactive, as the server enforces). The list reloads on its `saved` event |
| `selectedMember`, `deleteOpen` | Archive dialog, which is `MemberArchiveDialog` (also shared with the member page); the list reloads on its `archived` event |
| `filteredMembers` | `filterMembers` then `sortMembers` |
| `hasActiveFilters` | Any filter differs from its default; shows "Clear filters" |

## Client-side filter and sort
All in `frontend/src/utils/memberFilters.js`; all filters are ANDed.

| Aspect | Behaviour |
|--------|-----------|
| Search | Trimmed, case-insensitive substring on `name`, `email`, `phone`; also digits-only match on phone when the term has 3+ digits |
| Status | Chosen with a segmented control (below), not a dropdown. ALL = every listed member; any other value equals `member.status`. ARCHIVED (ADMIN only) switches the list to `archivedMembers`, loaded from `GET /api/members?archived=true` |
| Dues | Applies to members with status MEMBER only (`countsForDues`). "Behind" = MEMBER and `consecutiveMonthsMissed > 0`; "Paid up" = MEMBER and `=== 0`. Any other status never matches either (it still matches "All"): the server stops counting months for them, so the stored number is stale |
| Join date | Inclusive `YYYY-MM-DD` string compare on `joinDate`; members without a join date are excluded once either bound is set |
| Sort keys | `name` (locale, case-insensitive), `joinDate`, `consecutiveMonthsMissed` (the "Dues" column). Empty values sort last in both directions; members who are not MEMBER also sort last by dues in both directions |
| Sort toggle | Same key flips asc/desc; new key starts asc. Headers are buttons with `aria-sort` and a caret |
| Sort by select | In the filter row on every width (phones have no headers): Name (ascending), Most behind (`consecutiveMonthsMissed`, descending, so the most behind first; members who are not MEMBER last), Joined (`joinDate`, descending, newest first). It shares `sort` with the headers, so both stay in step. Last paid, Phone and Household are not sortable |

### Status segmented control
A `role="group"` "Filter by status" of buttons with `aria-pressed`, above the other filters: All, Member, Inactive, Transferred, Deceased and, for ADMIN only, Archived. Each shows its count, from the loaded `members` (`statusCounts` in `memberFilters.js`); "All" leaves out any member whose status is ARCHIVED, and the counts ignore the other filters. The Archived count shows only once the archived list has loaded (it loads when the segment is first chosen), and choosing it shows the Archived view. On a phone the row scrolls sideways and each button is 44px high. From lg up see "Desktop layout" below.

### Desktop layout (from lg), as the Members board
- Header: `PageHead band`: a full-width white band (64px, a rule under it), the title on the left, Export CSV and Add member (STAFF+) on the right, no subtitle. The band is the one place a page leaves the shell's 24px padding: the view's root drops it (`lg:p-0!`, `lg:max-w-none!`) and a content area (`max-w-[1400px]`, 24px top, 32px sides, 40px bottom) holds everything below the band. Below lg this is the normal compact header and the shell's padding.
- Filter row, one row (it wraps only when the page is too narrow for it): the status control on the left; on the right Dues (All dues / Paid up / Behind), Sort by, the search (a search icon inside, up to 240px, it shrinks first) and "More filters", all 32px with 13px text and visually hidden labels (the accessible names stay "Dues", "Sort by", "Search"). "Clear filters" follows when a filter is set.
- "More filters" opens a small popover under the row (right-aligned, above the table, does not move it) with Joined from and Joined to. It is a disclosure, not modal: Tab leaves it freely, Escape or the button closes it. The date filters work as before; the button carries the count of dates set.
- Between the filter row and the table, 16px each side, sits the selection bar (see Selection and bulk actions). The visible count moves to the table card's footer; the `aria-live` count line stays, screen reader only.

### Phone and tablet header and filters (below lg)
Breakpoints: the screen switches between stacked cards and the table at `lg` (992px), not `md`. The fixed rail takes 232px from `lg`, so a 768px tablet has the full width but the table's ten columns need more than that; below `lg` the tablet gets the phone layout (cards two across from `md`, one column below). Sized to the phone board (title row, search, one chips row, then cards), so the first member card is on the first screen at 390x844:
- Header: `PageHead compact`: no subtitle, tighter spacing, the title and a 44px "Add member" (STAFF+) on one row. "Export CSV" is not in the header here.
- Order: the status control, then the search (44px high, label visually hidden, placeholder kept) with the "Filters" button (44px) beside it. The panel under them (`#filter-panel`, a two-column grid) holds Dues, Sort by, Joined from and Joined to.
- "Clear filters" shows whenever any filter is set (search and status included), open or closed, as a 44px text button under the form.
- "Export CSV" (STAFF+) is a text button at the right of the count line ("N members") above the cards; same `exportMembers`, so it exports what is filtered. From lg up that line has no button and Export CSV stays in the header.
- The "Select all N shown" row, the card checkboxes and the `aria-live` count are unchanged.

## Table and list
- The name (table and phone card, not the Archived view) links to the member's own page, `/members/:id` ([member-detail-view](member-detail-view.md)).
- From lg up: a `<table>` inside a card (`rounded-md`, 1px `rule` border, `paper`, `overflow-x-auto`) with a `mist` header row and a hairline `rule` between rows. Columns: for STAFF+ a checkbox, Member (the name, sortable, with the email beneath in muted small text, Latin or Amharic as stored), Household (name, or a muted dash), Status (`StatusBadge`, the pill: "Member" fern, "Inactive", "Deceased", "Transferred" and "Archived" neutral), the year strip, Dues (sortable), Last paid (`lastPaymentDate` as a date, "Never" when none), Phone (last, hidden below xl) and, for STAFF+, the row menu. The Joined column is gone from the table (the date stays on the member page and the phone card; "Joined" is still a Sort by option).
- Below the rows, inside the card, a footer line: "N of N members" on the left (the filtered count of the shown list) and "Dates and counts as of <long date>" (`EEEE, d MMMM yyyy` of `today`) on the right.
- Dues cell (`duesCell`, table only): "N months" (`consecutiveMonthsMissed`, clay, "1 month") when behind; "Due now" (ochre text) when the count is 0, the status is Member, the member has joined by this month and no payment for the current month has loaded; otherwise "Paid up" (fern). Any status other than Member shows "No dues" (muted). "Due now" needs the payments list, so while it has not loaded a member with a count of 0 reads "Paid up". The phone card keeps `duesText` ("N months behind").
- Below lg the table becomes a list of cards (see Year strip and phone cards). For a member who is not MEMBER the dues word is left out there.
- Dates use `formatDate(date, 'MMM d, yyyy')`; a `YYYY-MM-DD` string is read as a local day, so it never shifts by a day west of UTC (`frontend/src/utils/index.js`).
- Below lg the "Sort by" select stands in for the headers.
- Between lg and xl (992px to 1199px, where the page beside the rail is 712px to 920px) the Phone column is hidden and the number sits under the email in the Member cell, and the table has a 56rem minimum width inside the card (`overflow-x-auto`), so it scrolls sideways in its own box instead of squeezing the columns or the page; the footer line scrolls with it. From xl the table fills the card.
- A member's household is its own column in the table (phone cards keep it under the name with the house icon); the add and edit dialog has a "Household" select that sends `householdId` only once touched (see [households](households.md#screen)).

## Year strip and phone cards
- Data: `api.getPayments()` (`GET /api/payments`, VOLUNTEER+) loads in parallel with the members and after every reload; `paidMonthsByMember` groups `payment.period` ("yyyy-MM") by `payment.member.id`. No backend change. If that call fails the strip cells show a muted dash and the rest of the screen works. It returns every payment, which is fine at this size; a per-member endpoint is the next step if the list grows large.
- `YearStrip` takes a `muted` prop (used by the Archived view): paid squares are grey (`field` at half strength), and no square is red or amber.
- Desktop: a column "Nov to Oct, one square a month" (the range follows the current month) with the compact `YearStrip`, before Dues. Sorting and filters are unchanged (the column is not sortable).
- Rule: see [../design.md](../design.md#year-strip) and the header of `frontend/src/utils/yearStrip.js`. Red squares are capped at the server's `consecutiveMonthsMissed`, so the strip and "N months behind" cannot disagree.
- Below lg each member is a card (two across from md, one column below): name, email, household, status, dues text, phone, join date and last paid ("Last paid never" when none), a 44px selection checkbox (STAFF+), the More menu, the large strip with month initials, then Call (`tel:`, shown with a phone) and Record payment (STAFF+, members who owe dues; links to `/payments?memberId=<id>`, which opens the Record payment sheet with that member chosen and the oldest unpaid month filled in, see [payments-view](payments-view.md#record-payment-on-a-phone)), both 44px high. The last card clears the bottom tab bar (`main` carries the bar's padding below `lg`).

## Archived view (ADMIN only)
- Status filter "Archived" loads `GET /api/members?archived=true` (see Filters). The count line reads "N archived members".
- A note first: "Archived members are hidden from the lists, dues, reminders and messages. Their payments and messages are kept. Only administrators see this view."
- From lg up a table: Member (name in muted ink, email beneath), Household ("None" without one), the year strip in its `muted` treatment, Last paid (`lastPaymentDate`, "Never"), Archived (`archivedAt` as a date; the API does not say who archived, so no "by"), and the actions Restore and Delete for good. Below lg each member is a card with the large muted strip and the same two buttons at 44px.
- Delete for good opens `ConfirmDialog` "Delete <name> for good?" (cannot be undone; only works for a member with no payments and no messages), confirm button "Delete for good" (danger, focus starts on Cancel).
- The button is disabled with "Has payments, so it stays archived" when the payments already loaded for the strips include that member (`hasPayments`; `GET /api/payments` shows an ADMIN the payments of archived members). A member with messages but no payments is still enabled and the server's 409 decides.

## Selection and bulk actions
STAFF+ only, normal list only (the Archived view has no checkboxes). `canSelect` gates it.
- Checkboxes: one per table row and card (the card's is a 44px target), plus a header checkbox (desktop) or a "Select all N shown" row (phone) that ticks every row on screen and shows the mixed state when only some are ticked. Only rows currently shown count as selected (`selectedMembers`): a member hidden by a later filter is not acted on. A ticked table row gets the teal tint.
- The bar appears at the first tick. From lg it sits between the filter row and the table card in a slot reserved for it (`min-h-14`, sticky under the top bar, STAFF+ only), so the table does not move when it appears or goes; there is an empty 56px slot (plus the 16px gaps) when nothing is ticked. If the buttons wrap on a narrow desktop the slot grows with the bar. Below lg the bar itself is sticky above the cards. It holds: "N selected", the first three names and "and N more", Clear selection, then Send message, Export selected, Mark inactive and, ADMIN only, Archive. A visually hidden `role="status"` `aria-live="polite"` line says "N selected" so screen readers hear the count change.
- Send message: a link to `/communications`. The Messages screen has no member parameter, so the selection is not carried over; pick the member there under "One member".
- Export selected: client-side, `membersCsv` (`frontend/src/utils/memberCsv.js`) builds the CSV of the selected rows in screen order with the server's header row `ID,Name,Email,Phone,Join date,Months behind,Status`, the same CSV escaping and a byte order mark; file `members_selected_<date>.csv`. No request is made, so unlike the server export it writes no "Exported N members" entry to the activity log.
- Mark inactive: `ConfirmDialog` "Mark N members inactive?" (N counts selected members with status MEMBER; the others are left as they are and the dialog says how many). The button is disabled when none of the selection is a Member. Uses `api.updateMember` with `buildMemberRequest({...member, status: 'INACTIVE'})` per member.
- Archive (ADMIN): `ConfirmDialog` "Archive N members?" (danger, "Archive N members"). Uses `api.deleteMember` per member.
- `runBulk`: one call at a time; a failure never stops the rest. Then both lists reload. All done: success toast ("Marked inactive" / "Archived", "N members"). Some failed: error toast "X of N members marked inactive|archived" naming the members that failed and the first error message (none for 403, where the shared handler already shows "Access denied"). The failed members stay selected so the action can be tried again; the others are deselected.

## Actions
- `loadMembers` -> `api.getMembers()`, sets `members`, sets `loadError` on failure. Runs in `created`, after every save, archive or toggle, and from "Try again".
- Row menu (`ActionMenu`, trigger label "More actions for <name>"): Edit, Mark inactive (Member) or Mark active (Inactive), Change status... (opens Edit with the Status select focused), Archive (ADMIN only, in clay). Archived members have no row menu: they live in the Archived view (below).
- `saveMember`: client checks (name required; the email is optional and checked with `isValidEmail` only when filled) show under the fields without a request. Then `buildMemberRequest(form)` (`frontend/src/utils/memberPayload.js`, sends `joinDate` when set) and `api.updateMember(id, request)` or `api.createMember(request)`, reload, close the dialog, toast "Member saved" / "Member added". The primary button shows "Saving..." and is disabled while the request runs.
  - Add mode defaults "Joined on" to today (local date, `max` today); edit mode shows the stored date.
  - Failure keeps the dialog open: each `error.fieldErrors` entry (`{field, message}`, set by the API interceptor) goes under the matching field (`name`, `email`, `phone`, `joinDate`); a field-less 400 and anything else goes in an `AlertBanner` at the top of the dialog plus an error toast "Could not save member" (no toast for 403: the shared handler already shows "Access denied").
- Archive: dialog "Archive <name>?" ("This hides <name> from the lists. Their payments and messages are kept."), buttons "Archive member" (danger) and "Cancel". Success: reload, close, toast "Member archived". Failure: banner in the dialog and toast "Could not archive member" (not for 403). `api.deleteMember` calls `DELETE /api/members/{id}`, which archives.
- `toggleStatus`: no dialog. `api.updateMember(id, buildMemberRequest({...member, status: INACTIVE or MEMBER}))`, reload, toast "Member deactivated" / "Member reactivated". Failure toast "Could not deactivate member" / "Could not reactivate member". Reactivating resets the months behind.
- `restoreMember` (ADMIN, Archived view): `updateMember` with `status: MEMBER` (`PUT /api/members/{id}`; this resets the months behind), reload both lists, toast "Restored" / "Could not restore member". The button is disabled while the request runs.
- `deletePermanently` (ADMIN, Archived view): `api.deleteMemberPermanently` -> `DELETE /api/members/{id}/permanent`, reload, toast "Deleted for good". On a 409 (payments or messages) the dialog closes and an error toast says "<name> has payments or messages, so they can only stay archived. Their history is kept."; the member stays. Other failures: toast "Could not delete member" (not for 403).
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
