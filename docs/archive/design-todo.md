> Finished 5 October 2026 (batches 1 to 3 built and the app now follows the boards). Kept for history. Current docs: [design.md](../design.md), [todo.md](../todo.md).

# Design Todo: all pages

*Written 4 October 2026. This is the plan for the design mockup only. App code is not part of it. The app backlog stays in [todo.md](../todo.md).*

Mockup: https://claude.ai/artifact/CBK4edth9DXvAqxfo4ZN9V (canvas "MemberTracker redesign", private). Source files live in the session scratchpad under `mt/project/` (`canvas.json` plus one `.dc.html` per artboard), so republish from there with the same `url`.

## Where we are

The app has 8 routes (`frontend/src/router/index.js`). The mockup covers 4 of them.

| Route | Role | In the mockup |
|---|---|---|
| `/dashboard` Overview | VOLUNTEER+ | Done (`Main`) |
| `/members` | VOLUNTEER+ | Done (`Members`) |
| `/households` | VOLUNTEER+ | Done (`Households`) |
| `/payments` | VOLUNTEER+ | Done (`Payments`) |
| `/communications` Messages | VOLUNTEER+ | **Missing** |
| `/activity` | ADMIN | **Missing** |
| `/profile` | any signed-in | **Missing** |
| `/login` Sign in | guest | **Missing** |
| member phone screen "My dues" | MEMBER | Done (`MyDues`), but **no such route exists in the app** |

Not designed at all: dialogs, empty, error and loading states, the 403 screen, and the staff view on a phone.

## Design rules to keep (taken from the finished boards)

- Colors: rail `#0B2E2F`, teal `#0F766E` (hover `#115E59`), page `#F2F4F3`, surface `#FFFFFF`, border `#DCE3E1`, ink `#10201F`, muted `#4B5B5A`. Month squares: paid solid teal, missed clay hatching, due now ochre outline, not a member yet dashed.
- Type: IBM Plex Sans with tabular numbers, Noto Sans Ethiopic for Amharic names. Names lead each row, in Amharic first.
- Density: staff screens are dense (32 px controls, 40 px rows). Phone screens are comfortable (44 px touch targets, 48 px main button, 16 px inputs).
- Month squares always carry a text label for screen readers.
- Copy: sentence case, action buttons say what they do ("Send to 7 people", not "Submit"), errors say what failed and how to fix it.
- Every artboard has hover, focus, disabled and error states, or says why one does not apply.
- Sample data stays consistent with the finished boards: 10 members, 5 behind, $25 a month, $100 collected of $250 in October, receipts `R-0000xx`.

## Decisions needed before the batch that depends on them

- [ ] **D1. My dues screen.** Keep it as a design only, or plan the app work (new route, `GET /me/dues`, a MEMBER landing page)? Needed before batch 3.
- [ ] **D2. Language.** English only, or add an Amharic/English toggle to the mockup? Needed before batch 1 (it changes every label).
- [ ] **D3. After the design.** Build it into `frontend/`? If yes, Phase 4 step 11 (people without a membership) goes first, because the Households board shows them.

Default if unanswered: design only, English only, decide on building later.

## Batch 1: the four missing routes (done 4 October 2026, artifact version 5)

Built as Messages, Activity, Profile, Login, plus ProfilePhone and LoginPhone. Defaults taken for D1 to D3 (design only, English only). Dialog links (confirm send, deliveries, change password) point nowhere yet: they are batch 2. The rail on the four finished boards now links to Messages, Activity and Profile. Not rendered or checked by eye, the type instructions forbid it unless you ask.

### 1.1 Messages (`Messages.dc.html`, 1440 x 1120)

- [x] Page head: "Messages", lead "Email members and see what was delivered."
- [x] Compose card: Send to (Everyone, Behind on dues, One member), "At least this many months behind" (shown only for Behind), Member picker (shown only for One member), Subject (200 characters), Message with the `{{member_name}}` token explained in a hint.
- [x] Recipient preview beside the form: count, then names with their month strips, and a line for people who will be skipped and why (no email).
- [x] Send button reads "Send to 7 people" and opens the confirm dialog (see 2.5).
- [x] Sent messages list: subject, audience, date, delivered / failed counts, a failed badge on the row, "Deliveries" link.
- [x] States: no messages yet, load error with "Try again", send disabled while the form is invalid.
- [x] Acceptance: a staff member can tell, without scrolling, who will get the message and how many.

### 1.2 Activity (`Activity.dc.html`, 1440 x 1000)

- [x] Admin-only note in the lead: "Only administrators can see this."
- [x] Filter row: type select, "Clear filter" link when active.
- [x] Entries grouped by day, newest first: who, what they did, to what (member name, household, export). Use the real type labels from `ActivityView.vue`.
- [x] "Show more" button with a busy state.
- [x] Empty state: "No activity matches this filter" with a Clear filter button. Empty log state: explains that entries appear after the first change.
- [x] Acceptance: an admin can find "who archived this member" in two clicks.

### 1.3 Profile (`Profile.dc.html`, 1440 x 800 plus a 390 phone variant)

- [x] Your details: first name, last name, phone (hint "Optional. 10 digits or more."), email shown read-only. Save and Cancel.
- [x] Password card with "Change password" (opens the dialog in 2.4).
- [x] Error variant: load failure with "Try again". Field error variant: phone too short.
- [x] Phone variant: single column, 48 px buttons.

### 1.4 Sign in (`Login.dc.html`, 1440 x 900 plus 390 phone)

- [x] Split layout: the congregation name in Amharic and a short line on one side, the form on the other. No marketing copy.
- [x] Email, password, Sign in button. One error message only ("Email or password is wrong"), matching the backend's single login error.
- [x] Variants: locked after repeated attempts (15 minute unlock), session expired, signing in (busy).

## Batch 2: dialogs and states (done 4 October 2026, artifact version 6)

Built as three boards: DialogsMembers (2.1, 2.3), DialogsPayments (2.2, 2.4, 2.5, plus a receipt), States (2.6). Two things go beyond today's app: "Delete for good" on archived members (the API exists, admin only, 409 with history; there is no screen for it) and the way the failed-first order is shown in Deliveries. Not rendered or checked by eye.

### 2.1 Add and edit member (`Dialogs.dc.html`)
- [x] Fields: first name, last name, email (optional, may be shared), phone, join date (with the date helper text), household select, status (edit only).
- [x] Error variants: missing name, bad email, duplicate member.
### 2.2 Record payment
- [x] Member (preselected when opened from a row), months covered, amount ($25 default), method, date (no back-dating past the allowed window), receipt number preview.
- [x] Error variants: member not active, duplicate payment for the month.
### 2.3 Archive member
- [x] Confirm dialog. Explains archive keeps history. Admin-only "Delete permanently" shown as a separate, red action with the 409 case ("This member has payments, so they can only be archived").
### 2.4 Change password
- [x] Current, new, confirm; rules shown as a hint; mismatch and wrong-current errors.
### 2.5 Send message confirm and deliveries
- [x] Confirm: "Send to 7 people?" with the audience stated.
- [x] Deliveries modal: totals row, per-recipient status, Retry on failed rows.

### 2.6 States board (`States.dc.html`, 1440 x 900)
- [x] Four variants of each, side by side: empty list, load error with "Try again", loading, 403 ("You don't have access to this page", with the way back).
- [x] Toasts: saved, failed, undone.

## Batch 3: phone, archived, linking (done 4 October 2026, artifact version 7)

Built: StaffPhoneMembers, StaffPhonePay, StaffPhoneMore (3.1), MembersArchived (3.2), a My dues pass (3.3, links plus a Sign out button), the link pass (3.4) and the canvas update (3.5). Added beyond the plan: DialogsHouseholds (add household, add person, make a member, refused delete), because the Households screen opens them. On the phone only Members, Record payment and More are drawn; the tab bar links Overview, Payments and Messages to their desktop boards. Not rendered or checked by eye.

- [x] **3.1 Staff on a phone** (`StaffPhone.dc.html`, 3 artboards at 390): navigation with a bottom bar instead of the rail, Members as stacked rows that keep the month strip, Record payment as a full-screen form.
- [x] **3.2 Archived members view:** the Members board filtered to archived, deceased and transferred, with the strips greyed and a "Restore" action.
- [x] **3.3 My dues** (only if D1 says to keep it): one pass to align it with the Profile and Sign in boards.
- [x] **3.4 Link pass:** every rail item and every button that opens a dialog goes to its board in play mode. Check the canvas lays out cleanly (no overlap) with the new artboards.
- [x] **3.5 Update `canvas.json`:** add the new boards to the grid, keep the note t1 current, then republish once.

## Per-board checklist (apply before each publish)

- [ ] Hover, focus, disabled and error states present or noted as not applicable
- [ ] Text contrast at least 4.5:1, month squares labelled in text
- [ ] No more than one memorable element per board, the year strip
- [ ] Copy follows the rules above, actions named consistently across boards
- [ ] Sample data matches the other boards
- [ ] Phone boards use 44 px targets and 16 px inputs
- [ ] Do not render or screenshot the artifact unless asked (the type instructions forbid it)

## Order of work and size

| Step | Output | Size |
|---|---|---|
| Answer D1 to D3 | decisions | 5 minutes |
| Batch 1 | 4 new boards | about 1 session |
| Batch 2 | dialogs and states boards | about 1 session |
| Batch 3 | phone, archived, links, republish | about half a session |

## Out of scope

- Any change to `frontend/`, `src/main/`, migrations, or tests.
- Phase 4 steps 11 and 12 (see [todo.md](../todo.md) and [person-membership-plan.md](person-membership-plan.md)); they start only after the design is approved and you say so.
