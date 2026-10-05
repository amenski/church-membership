> Finished 5 October 2026 (every gap is built or decided). Kept for history. Current docs: [design.md](../design.md), [todo.md](../todo.md); the two gaps still open (a second name field, dues amount setting) are listed in the todo.

# Design gaps: app against the artifact

*Audited 4 October 2026 in Chrome: the running app (localhost:3000, 1440 px) against the artifact boards. Artifact: https://claude.ai/artifact/CBK4edth9DXvAqxfo4ZN9V (version 9, five pages). Plan: [design-todo.md](design-todo.md).*

Legend: **S** a missing screen, **F** a missing feature, **V** visual only. Size: small (under an hour of agent work), medium, large.

## Already matching (checked)

Rail, Sign in, Overview ledger and facts strip, Call this week, phone tab bar, More, Record payment sheet on phones, archived view, People on the household detail, My dues. Typeface is IBM Plex Sans.

## Gaps by screen

### Overview
- [x] **V** The board's top bar has the date and a member search. Built 4 October 2026 in `App.vue` (desktop from `lg`, STAFF/VOLUNTEER and up); not checked by eye in a browser.
- [x] **V** Cards only (5 October 2026): the Collected by month chart left the Overview (the board has none); Call this week, Latest payments (name, "Oct 2026, Cash, paid Oct 4", amount, All payments link) and Recent activity are bordered cards like the ledger. From `xl` ledger + activity left, calls + payments right; below `xl` one full-width column (a 21rem column left the ledger about 2px for the name at 1000px). Recent activity is not on the board; kept under the ledger. Not checked by eye in a browser.
- **F** "Collected in October $100 of $250 expected" needs a dues amount the app does not have. Skip until a dues setting exists.

### Members
- ~~**F** Status filter as tabs with counts (All 11, Member 10, Inactive 1 ...) instead of the dropdown. Small.~~ **Done** (4 October 2026): segmented control with counts, Archived for ADMIN only.
- ~~**F** Row checkboxes and a bulk bar (Send message, Export selected, Mark inactive, Archive). Medium.~~ **Done** (4 October 2026), STAFF+ (Archive ADMIN). Send message links to /communications without preselecting: Messages has no member parameter yet.
- ~~**F** Household column and Last paid column. Small.~~ **Done** (4 October 2026).
- ~~**F** Sort by Most behind. Small.~~ **Done** (4 October 2026): a Sort by select (Name, Most behind, Joined), also on phones.
- ~~**S** Member detail page (desktop and phone): two-year ledger, payments, history, household card. Names do not link anywhere today. Large.~~ **Done** (4 October 2026): `/members/:id`, see [features/member-detail-view.md](../features/member-detail-view.md). Names in Members and the Overview ledger link to it. The History card is not built: the activity log is ADMIN only and has no per-member filter.
- **V** Amharic name above the Latin name: the data has one name field. Needs a decision (a second name field or none).

### Households
- [x] **V** Master-detail from `lg`: list left (name, city, member count), the household on the right in the page with members (linked names, status badge, compact strip, months behind) and people without a membership; below `lg` list or household, chosen by `?id=`. Built 4 October 2026; the member page's "Open household" links to `/households?id=<id>`. Not checked by eye in a browser. The md-to-lg table was dropped for the one list.
- [x] **F** Behind-on-dues notice inside a household ("Dues are per member: N members in this household owe M months in total."). Built 4 October 2026.

### Payments
- [x] **F** The 12-month "Collected by month" bars above the table, the Overview's chart extracted to `CollectedChart` and shared; the three stat tiles are slim as on Overview. Built 4 October 2026. Redrawn as vertical columns in a card like the board (5 October 2026); amounts abbreviate below `md`; not checked by eye in a browser.
- **V** Receipts column opens a dialog; fine. Table spacing is close to the board.

### Messages
- [x] **F** Audience choices: Everyone, Behind on dues (with "at least N months"), One member. The backend already supported all three (send-to-all, send-to-overdue/{months}, send-to-member/{id}); only the labels changed. Built 4 October 2026.
- [x] **F** Recipient preview beside the form: who gets it, who is skipped for no email, with strips. Built 4 October 2026 from the members and payments already loaded; the send button and confirm read "Send to N people" and name who is skipped. Not checked by eye in a browser.
- [x] **V** Two-column layout (form left, preview right) from `lg`, stacked below. Built 4 October 2026.
- [x] **F** Sent list: delivered and failed badges on the row; the deliveries dialog lists failed first. Built 4 October 2026.

### Activity
- [x] **V** Entries grouped by day with a type badge. Built 4 October 2026 (day cards, `HH:mm`, tone badges, actor); not checked by eye in a browser.
- **V** The app shows 50 raw rows including test noise; the board shows 5 grouped days. Small.

### Profile
- **V** Not audited by eye. The board has a field error example and a password card. Check.

### Sign in
- [x] **V** Split layout with a deep-teal panel, the year strip as brand mark and the congregation name large. Built 4 October 2026 (desktop from `lg`, phone header below). Checked in Chrome at 1440, 1000 and 390 px; the error and busy states were not re-checked by eye.
- **F** Locked and session-expired variants exist in the app as banners; compare copy.

### More and My dues
- Matching in structure. My dues was only seen as the admin matching a test member; a real MEMBER sign-in has not been exercised.

### Dialogs and states
- **V** Not audited by eye. The dialogs exist in the app; the boards add wording and error states. Check each against its board.
- **F** "Delete for good" on archived members is built (step 4).

## Cross-cutting

- **Demo data** makes the app look unlike the boards: the members have two months of payments, so most strip squares are dashed. Seeding 12 months of payments and Amharic names would make every screen read like its board. Needs the owner's OK.
- **Artifact**: opens at the first page of five now; it still takes about 35 seconds to load, which is the editor's weight, not the boards.

## Suggested build order

1. ~~Member detail page (S, large).~~ Done.
2. ~~Sign in split layout (V, medium).~~ Done.
3. Members: status tabs, Household and Last paid columns, bulk bar (F, medium).
4. ~~Messages: audience choice, recipient preview, row badges (F, medium).~~ Done.
5. ~~Households master-detail and Payments chart (V/F, medium).~~ Done.
6. ~~Activity day grouping, Overview top bar (V, small).~~ Done.
7. Demo data seed, then a last pass of every screen in Chrome.
