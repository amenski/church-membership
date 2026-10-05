# Design notes: Felege Selam

Subject: Felege Selam Church (ፈለገ ሰላም, Amharic for "stream of peace"). The app is an internal
admin dashboard: the treasurer and volunteers open it weekly after services to see who needs a
call, record payments and reach members; a signed-in member sees only their own dues and profile.

Code: `frontend/src/assets/styles/tailwind.css` (the only stylesheet: tokens and base rules),
the components in `frontend/src/components/`, and the shared class strings in
`frontend/src/ui/classes.js`.

## Look

A restrained, dense admin surface: neutral greys, one accent (teal) and three status colours used
only for status. No gradients, no decorative illustration, no serif display face, no animation
beyond the spinner. Structure carries the meaning: a compact top bar, a persistent dark-teal left rail, a
rule under every page header, and tables for anything that repeats.

## Tokens

Each is a `--color-*` variable in the `@theme` block of `tailwind.css` (`--color-mist` is
`bg-mist`, `text-mist`, `border-mist`). Every default Tailwind palette and scale is zeroed first
(`--color-*: initial`), so nothing outside this list exists.

| Token | Hex | Use | Contrast (checked with the WCAG 2.x formula) |
|---|---|---|---|
| `--color-mist` | `#F6F7F9` | page background | ink on mist 16.55 |
| `--color-paper` | `#FFFFFF` | surfaces: tables, cards, dialogs | ink on paper 17.74 |
| `--color-ink` | `#111827` | body text, headings | see above |
| `--color-muted` | `#4B5563` | secondary text, table headers, labels | 7.56 on paper, 7.05 on mist |
| `--color-rule` | `#E5E7EB` | 1px rules and card borders (decorative) | n/a |
| `--color-field` | `#6B7280` | form-control borders | 4.83 on paper, 4.51 on mist (needs 3.0) |
| `--color-teal` | `#0F766E` | the accent: primary, links, active nav, focus ring | 5.47 on paper, 5.11 on mist; white on teal 5.47 |
| `--color-teal-hover` | `#115E59` | hover and pressed | white on it 7.58 |
| `--color-teal-tint` | `#F0FDFA` | active nav and hover background | teal on it 5.25 |
| `--color-rail` | `#0B2E2F` | the side rail background, the one dark surface | rail-text on it 10.87, white 14.52 |
| `--color-rail-hover` | `#12403F` | rail item hover background | rail-text on it 8.58, white 11.47, rail-muted 5.81 |
| `--color-rail-line` | `#1B4B4A` | the rule above the account block (decorative) | n/a |
| `--color-rail-text` | `#CFE3E0` | rail item text and Sign out | see above |
| `--color-rail-muted` | `#9FBFBB` | rail icons, the Latin name, the email | 7.36 on rail |
| `--color-rail-accent` | `#2DB5A8` | a paid square on the rail colour (the sign-in brand strip); a graphic fill, never text | 5.73 on rail |
| `--color-fern` | `#15803D` | paid fill / success button | 5.02 on paper |
| `--color-fern-text` | `#166534` | "paid" text | 7.13 on paper, 6.81 on fern tint |
| `--color-ochre` | `#D97706` | warning fills and bar segments only, never text | 3.19 on paper (graphic only) |
| `--color-ochre-edge` | `#B45309` | warning borders | 5.02 on paper |
| `--color-ochre-text` | `#92400E` | "behind" text | 7.09 on paper, 6.84 on ochre tint |
| `--color-clay` | `#B91C1C` | danger: destructive actions, failed deliveries | 6.47 on paper, 6.04 on mist |
| `--color-clay-hover` | `#991B1B` | danger hover and pressed | white on it 8.31 |
| tints | teal `#F0FDFA`, fern `#F0FDF4`, ochre `#FFFBEB`, clay `#FEF2F2` | badge, alert and tile backgrounds | text on each tint is at least 5.9 |
| alert lines | teal `#99F6E4`, fern `#BBF7D0`, ochre `#FDE68A`, clay `#FECACA` | 1px outline of an alert, decorative | n/a |

Every text pair is at least 4.5:1; every control border is at least 3:1. Ochre is a fill: it never
carries text or meaning on its own. Only a very faint shadow exists, on modals
(`--shadow-modal`). Radii are 4px (`rounded-sm`) for controls and badges and 6px (`rounded-md`)
for surfaces.

## Type

IBM Plex Sans (npm `@fontsource/ibm-plex-sans`, weights 400/500/600), self-hosted, no CDN, with
tabular figures on (`font-variant-numeric: tabular-nums` on `body`) so columns of numbers line up.
Noto Sans Ethiopic (400/500/600, plus 700 for the wordmark) follows it in the font stack, so Amharic
names and text render in a matching face; the browser fetches the Ethiopic files only when a page
holds Ethiopic characters. The wordmark ፈለገ ሰላም is set in Noto Sans Ethiopic 700.

Scale: 12 / 13 / 14 / 16 / 18 / 22 / 28 px (`text-xs` … `text-3xl`). Body is 14px. Headings are
the same family at weight 600 with a slight negative tracking; there is no display face. Table
headers are 12px medium muted. Form controls are a fixed 16px (`text-lg`) whatever the density,
because iOS zooms a page whose inputs are smaller.

## Density

Two densities share one palette and one type family. They are plain CSS custom properties in
`tailwind.css`, switched by a `data-density` attribute (not `@theme`: they change at runtime).
Utilities read them with `h-(--control-h)`, `min-h-(--row-h)`, `p-(--card-pad)`,
`text-(length:--text-body)`.

| Property | dense (default) | comfortable |
|---|---|---|
| `--control-h` | 32px | 40px |
| `--control-primary-h` | 32px | 44px |
| `--row-h` | 40px | 48px |
| `--list-row-h` | 44px | 52px |
| `--card-pad` | 16px | 16px (24px from 768px) |
| `--text-body` / `--lh-body` | 14px / 20px | 16px / 24px |
| `--text-label` / `--lh-label` | 12px / 16px | 13px / 18px |

- **Dense:** every staff screen. **Comfortable:** sign-in, and a MEMBER's own screens (My dues and Profile).
- `App.vue` sets `data-density` on the shell root: `comfortable` when signed out or when the role
  is MEMBER, otherwise `dense`.
- `BaseButton` consumes the density height: `primary` and `danger` hold `--control-primary-h`,
  `secondary` `--control-h`, and `size="sm"` stays padding-sized. `min-height` never shrinks a
  button, so a dense screen is unaffected by the comfortable values.
- Dialogs are teleported to `body`, outside `data-density`, so their controls keep the dense
  (root) height at any density.

## Layout

- **Top bar** (`App.vue`): sticky, 48px, a breadcrumb on the left (each route declares
  `meta.title`; it falls back to nothing). Below `lg` the same bar carries the brand; a signed-in
  user without the tab bar (a MEMBER) also gets the name (linking to Profile) and Sign out on the
  right, staff find both in More. From `lg`, VOLUNTEER and above get today's date and a "Search
  members" box on the right (32px control, visually hidden label, Enter goes to `/members?search=`).
- **Rail**: deep teal (`rail` tokens), fixed, 232px, below the top bar, from `lg` up only (below `lg`
  it is `display: none`; there is no drawer). Top: the congregation name in Amharic (white, Noto Sans
  Ethiopic) over "Felege Selam", linking home. Then the role-based items (Activity is ADMIN only); the
  active one is a solid `teal` fill with white text (5.47), hover is `rail-hover`. Bottom: the signed-in
  name linking to Profile, the email, and Sign out. Focus rings on the rail are white (`outline-paper`,
  14.52 on rail) because the teal ring would nearly vanish on the dark ground. There is no separate
  Profile item: the name is the link.
- **Bottom tab bar** (`components/BottomTabs.vue`, below `lg` only, VOLUNTEER and above): fixed, 60px
  plus the bottom safe-area inset (`viewport-fit=cover` in `index.html`), `paper` with a `rule` top
  border, `z-[1030]`. Five equal tabs: Overview, Members, Payments, Messages, More, each an icon over a
  12px label, 60px high. The active tab has a 3px `teal` top border and `teal` text and
  `aria-current="page"`; More is also lit (`aria-current="true"`) on Households, Activity and Profile,
  the pages it opens. `main` gets `60px + safe area` bottom padding so the bar never covers the last
  row. Staff see the same five at every role; More lists only what the role may open
  (see [features/more-view.md](features/more-view.md)).
- **Content**: offset by the rail, centred, `max-w-[1400px]`, 24px padding.
- **Full-screen sheet** (`BaseModal sheet`): below `lg` the dialog fills the screen: a `rail` top bar
  (56px plus the top safe area) with a Back chevron (the same Close button, 44px) and the title in white,
  a scrolling `mist` body, and a pinned `paper` footer with the buttons stacked full width at 48px, the
  primary on top (the footer slot order is Cancel then primary; it is reversed by `flex-col-reverse`).
  From `lg` it is the normal centred dialog. The Record payment dialog is the first user.
- **Sign-in** (`views/LoginView.vue`, signed out, no shell): from `lg` a split screen, the left half `rail`
  with the decorative year strip (ten `rail-accent` squares, one hatched, one outlined, `aria-hidden`) and
  its caption at the top, the name in Amharic (56px, 72px from `xl`), "Felege Selam" and a line about the
  church office at the bottom; the right half the form card centred, with the "Accounts are set up by the
  church office." note under it. Below `lg`: a short `rail` header (Amharic name 36px, "Felege Selam"),
  then the card; inputs and button 48px, inputs 16px. Text on the rail is `rail-text` (10.87) or white.
- **Page header**: `PageHead` (title, optional lead, action slot) with a rule under it. `compact` is the phone and tablet header (below `lg` only, the same breakpoint as Members' stacked cards): the lead is hidden, spacing is tighter and the action is centred on the title, so a list screen's first card shows on the first screen; a secondary action moves into the screen body. Members uses it.
  `band` (Members, Payments, Messages, Activity, Households and the Overview; not the member page, My dues, Profile or More) makes the header a full-width white band from `lg`: 64px, `rule` under it, title left and actions right, no lead (the lead stays below `lg`; Activity moves its sentence to the top of the content area). A screen with a band drops the shell's padding on its root (`lg:p-0! lg:max-w-none!`) and pads its own content area under the band (centred, `max-w-[1400px]`, 24px top, 32px sides); the top bar stays above the band. Below `lg` it is the normal header.
- **Table in a card** (Members, Payments and the Messages history from `lg`; the rule for a list screen that wants the board look): the table sits in a `rounded-md` card with a 1px `rule` border on `paper` and `overflow-x-auto` (it scrolls inside the card, never the page); the header row is `mist` (`TABLE_CARD_TH`), body rows are `paper` with a hairline `rule` between them (`TABLE_CARD_TD`: 16px at the edges, 10px above and below), and a footer closes the card inside the border: the `Pager` (a list that can grow) and, where it exists, a 13px `muted` line such as "Dates and counts as of ...". Status in such a table is the `StatusBadge` pill. A screen with a table card keeps its earlier table (Payments, md to lg) or list (Messages) below `lg`.
- **Facts strip** (Overview from `lg`): the four `StatTile`s (`cell`) become one `rounded-md` card with a 1px `rule` border, the cells equal in width and divided by 1px hairlines (the `dl` is `bg-rule` with `gap-px`, each cell `paper`, no border of its own, 24px figures). Below `lg` they are four separate tiles. Payments keeps its three separate slim tiles.
- **Selection bar** (Members from `lg`; the rule for a bulk-action bar on a list): it floats, it does not reserve a slot. `position: fixed`, 16px from the viewport bottom, centred over the content area right of the rail (`left-[232px]`, 24px side padding, `max-w-[960px]`), `paper` card with a `rule` border and `shadow-modal`, `z-[1050]` (above content, below the row menu 1100 and dialogs 1200), slide-up of 150ms that the global reduced-motion rule flattens. The table gets bottom margin while the bar shows so the last row is never covered. Below `lg` the bar stays in the flow: teal-tinted and sticky under the top bar above the cards.
- Tables repeat the same shape: `ui/classes.js` `TABLE` / `TABLE_TH` / `TABLE_TD`, rows at
  `--row-h`, with a stacked card list below `md` for the table screens (Payments); Members keeps its cards up to `lg` (`TABLE_FROM_LG`; two columns from `md`) because the table needs more than the page beside the rail has at 768px to 991px.
- **Households** is the exception: from `lg` a master-detail page, the list on the left (22rem) and the chosen household on the
  right in the page (address, notice, members with strips, people without a membership), chosen by `?id=` in the URL; below `lg`
  the list and the household are one screen each, with an "All households" link back.

## Components

`PageHead`, `SectionTitle`, `BaseButton`, `BaseInput`, `BaseSelect`, `BaseTextarea`, `BaseModal`,
`ConfirmDialog`, `ActionMenu`, `AlertBanner`, `EmptyNote`, `StatusLabel`, `StatusBadge`, `YearStrip`, `TextButton`, `StatTile`, `CollectedChart`,
`Pager`, `SortButton`, `MemberPicker`, `ReceiptDialog`, `MemberFormDialog`, `MemberArchiveDialog`, `BottomTabs`, `Icon`, `RuledList`/`RuledRow`, `BrandMark`, `RailLink`, `ToastHost`.

- `Icon` holds the whole icon set as inline SVG path data — no icon font, and only the glyphs named
  reach the bundle.
- `CollectedChart` is the "Collected by month" card (it carries its own `CARD` frame): twelve vertical columns, oldest to newest, amount above and month below, the current month hatched with a teal outline and "in progress" in a line under the chart, a visually hidden table for screen readers, abbreviated amounts ("$1.3k") below `md` so 12 columns fit a phone. It loads `GET /api/dashboard/collected-by-month` itself and owns its error and empty states. Only Payments uses it; the Overview has no chart.
- `StatTile` is the one place a large figure is styled; it sits inside a `<dl>`. `slim` shrinks it (20px figure, less padding) for the Overview's facts strip, whose tiles are Collected in <month>, Paid up (N of M members), Behind on dues (N members, X months unpaid) and Reminders (failed deliveries, with a Review link to Messages). Payments uses the same slim tiles for its three figures. There is no "expected" amount: the app has no dues amount to compare with.
- `StatusLabel` is a coloured dot **plus a word**, never colour alone. Tones: `paid` (fern),
  `behind` (ochre), `danger` (clay), `muted` (neutral — inactive, transferred, deceased, archived).
- `StatusBadge` is the pill form of the same tones (tint, line and text of the tone, 4px radius, the word inside): a count or a state on a row, as "3 delivered", "1 failed" and "Will get the email" on Messages.

## Year strip

`YearStrip` (`components/YearStrip.vue`, logic in `utils/yearStrip.js`): twelve squares, the last
twelve months ending with the current month, oldest first. No new tokens: every square uses the
existing ones, and state is carried by fill and border pattern as well as colour.

| State | Square | Screen reader text |
|---|---|---|
| paid | solid `teal` | Paid |
| missed | hatching of `clay-tint` and `clay`, 1px `clay` border | Missed |
| due now (current month, unpaid) | `paper` with a 2px `ochre-edge` outline | Due now, unpaid |
| not a member yet, or not owing dues | dashed `field` border | Not a member that month |
| unpaid, no longer counted by the server | dashed `field` border | Unpaid, no longer counted |

The Overview ledger lists every member who is behind or has not paid this month, with the Call and Send reminder actions on the behind rows; from `xl` (1200px) the "Latest payments" card sits beside it, below `xl` both cards are one full-width column. Every block on the Overview is a `CARD` (same border, radius and padding as the ledger). From the 2xl breakpoint (1400px) it is the "Dues by month" grid: `ledger` squares, a header row of month names, the legend (Paid, Missed, Due now, Not a member) at the card's top right and a footer row "Members who paid" (dues-paying members with a payment in each month). Below 1400px and on phones it keeps the compact strip.

Sizes: `compact` 10x18px with a 2px gap (table rows), `large` 20x26px with a 3px gap and the month
initial underneath (phone cards, initial is `aria-hidden`), `ledger` 28px squares with a 4px gap and
no initials (the parent draws the month names once above the column), `detail` 44x36px squares with the month name
above each, 12 to a row (`months=24` draws two rows with a caption such as "Nov 2024 to Oct 2025"; the member page, from `lg`). Each square has a visually hidden
"Oct 2026: Paid"; the container is a `role="group"` named per member.

Rule (also in the util header): a past unpaid month on or after the join month is "missed" only for
the most recent `consecutiveMonthsMissed` unpaid months, so the strip never shows more red than the
server's "N months behind". The current month is never missed (the server counts it on the 1st of
next month). A member who is not a MEMBER shows no red or amber.

`muted` (the Archived view): paid squares are `field` at 50% opacity instead of teal; missed and due also fall back to the dashed edge, so nothing is teal, red or amber. No new token.

## Member picker

`MemberPicker` (`components/MemberPicker.vue`, search in `utils/memberSearch.js`) replaces a native select wherever a member is chosen from a long list (Record payment, Messages "One member"). It is a text input with `role="combobox"` and a listbox (ARIA 1.2: `aria-expanded`, `aria-controls`, `aria-activedescendant`), the same border, 16px text, focus ring and clay error line as `BaseInput`. The input is 44px high below `lg` and the 32px control height from `lg`; result rows are at least 44px below `lg`. The list shows at most 8 rows, each the name, a muted line (household, phone) and a `StatusBadge` for the dues state: "N months behind" clay, "Due this month" ochre, "Paid up" fern, so the word always carries the meaning. The chosen member shows as the read-only input value with a clear button (44px below `lg`). The list is fixed under the input, in the dialog element when there is one (so `aria-modal` does not hide it and the scrolling dialog body does not clip it), and flips above the input when there is more room there. The list opens with no row highlighted (`aria-activedescendant` is absent): Arrow Down highlights the first row, Arrow Up the last, both wrap, Home and End jump to the ends, and hovering a row highlights it. Enter picks the highlighted row, or the one result when there is only one, and otherwise does nothing; it never submits the form while the list is open. Escape closes only the list when it is open. No new tokens.

## Pager

`Pager` (`components/Pager.vue`, logic in `utils/paging.js`, URL state in `utils/queryPaging.js`) pages every list that can grow: Members, the Households list, Sent messages and a message's deliveries page a list that is already in the browser (the caller slices it with `pageSlice`); Payments pages on the server. The Pager only needs `page`, `pageSize` and `total`, so the two modes are the same component: in server mode the caller passes the server's `totalElements`, keeps the last total while the next page loads, and asks the server for the slice.
- **Looks**: one `<nav aria-label="Pagination">` row. Left, "Showing 26 to 50 of 134" (`aria-live="polite"`). Right, a "Rows per page" select (10, 25, 50, 100; the label is visible) and a list of buttons: Previous, the numbered pages, Next. The numbers are at most 7 items with an ellipsis ("1 ... 4 5 6 ... 12"); the first and last page are always there; the current page is the filled teal button with `aria-current="page"`; Previous and Next are `disabled` at the ends. Buttons and the select are 44px high below `lg` and the control height (32px) from `lg`; select text is 16px below `lg`. Colours and borders are the segmented control's, no new token. The row wraps; below `sm` the numbers give way to "Page 4 of 12" between Previous and Next (seven 44px buttons do not fit a phone), and `compact` (a dialog, or a 22rem column) does the same at any width.
- **Where it sits**: a table card's footer (`px-4 py-3`, above any "as of" line), under a stacked card list or a list outside a card (the caller passes the spacing as `class`; the pager draws none). It replaces a footer's "N of N" count line.
- **Defaults**: 25 rows a page (Members, Payments), 10 (Households, sizes 10, 25 and 50; Sent messages), 25 inside a dialog. A list that is cut to a short head with a link (the Overview ledger, 10 rows) does not page.
- **Hidden** when the whole list fits in the smallest size (10 rows or fewer). With more rows than that the count line and the "Rows per page" select are always there, even when everything fits the current size (so a user who chose 100 can go back to 10, and is never trapped); the Previous, numbered and Next buttons only show when there is more than one page.
- **URL**: a list on a screen keeps `?page=2&size=50` (left out at page 1 and the screen's default size) with `router.replace`, so Back and a refresh keep the place and other query keys (`?id=`, `?dues=`) stay. Changing a filter, search, sort or tab goes back to page 1; a page beyond the last becomes the last. A dialog's pager has no URL state. A **server-paged** screen (Payments) also keeps its search, filter and sort in the URL (`?page=&size=&search=&method=&sort=`, each left out at its default) through the screen's `pagingExtraQuery()`, sends them to the server, and: waits 300 ms after the last key before it searches, ignores an answer that is older than the newest request, keeps the old rows on screen dimmed (`opacity-60`, `aria-busy`) while the next page loads, and has an empty state, and an error state with Try again. Sortable column headers are `SortButton`s with `aria-sort` on the `th`.
- **Emits** `update:page` and `update:pageSize`; the page and size are the caller's.

## Dialog and interaction rules

- Every dialog is a `BaseModal`: a 18px semibold title, a close button, one primary action in the
  footer with `Cancel` beside it. Never `window.confirm` or `alert`.
- `ConfirmDialog` is for an action that cannot be taken back or that reaches other people (sending
  email to members, deleting). Focus lands on Cancel, so a stray Enter never confirms.
- An action that is easy to undo does not get a dialog: it acts at once and toasts.
- Focus is always visible: a 2px teal outline with a 2px offset on every interactive element.
- `prefers-reduced-motion` disables every transition and animation.

## Loaded and empty states

Every list has an empty state that names the next step, and every screen that loads shows a
`role="status"` line while it does. A failed load shows an `AlertBanner` with a retry action.

## Decisions

| Date | Decision | Why | Status |
|---|---|---|---|
| 2026-10 | The landing page was removed; `/` redirects to `/login` and the guard sends a signed-in visitor to their role home | The product is an internal tool; a marketing splash was a dead end that only held a Sign-in button | In use |
| 2026-10 | Alegreya and Alegreya Sans were replaced by Inter, and the woven-band strip, the dues meter and the only keyframe were removed | A dense admin reads better in one UI sans with no decorative signature; the Overview now leads with stat tiles | In use |
| 2026-10 | The UI font changed from Inter to IBM Plex Sans, with Noto Sans Ethiopic as the Amharic fallback and tabular numbers on | The approved mockup is set in IBM Plex Sans; tabular figures keep the dues and amounts columns aligned | In use |
| 2026-10 | `bootstrap-icons` was replaced by an inline-SVG `Icon` component | One icon font shipped 314 KB for ~15 glyphs | In use |
| 2026-10 | The two-density tokens were kept rather than collapsed to one scale | Sign-in and a MEMBER's profile are still comfortable, and the tokens are the lever for that | In use |
