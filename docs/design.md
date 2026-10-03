# Design notes: Felege Selam

Subject: Felege Selam Church (ፈለገ ሰላም, Amharic for "stream of peace"). The
app has one job: show who needs a call, record payments, reach members kindly.
Users are the treasurer and volunteers who open it weekly after services (dense,
repeat use), and members who only see their own profile.

Code: `frontend/src/assets/styles/tailwind.css` (Tailwind tokens, see Styling system), `frontend/src/assets/styles/theme.css` (legacy Bootstrap overrides, removed in T4),
`frontend/src/components/WovenBand.vue`, `frontend/src/components/DuesMeter.vue`,
`frontend/src/utils/dashboardMeter.js`.

## Tokens

All are CSS custom properties on `:root` (theme.css). Ratios are WCAG 2.x,
computed by a script (`/private/tmp/claude-502/design/contrast.mjs`, not in the repo).

| Token | Hex | Use | Contrast on usual background |
|---|---|---|---|
| `--felege-mist` | `#F2F5F2` | page background | ink on mist 12.36 |
| `--felege-paper` | `#FFFFFF` | surfaces: cards, rail, tables, modals | ink on paper 13.57 |
| `--felege-ink` | `#14323A` | body text, headings | see above |
| `--felege-muted` | `#4F6870` | secondary text, table headers | 5.92 on paper, 5.39 on mist |
| `--felege-rule` | `#D9E0DC` | 1px rules, card borders (decorative) | n/a |
| `--felege-field` | `#6F8680` | form-control borders (added: rule is too faint for a control edge) | 3.89 on paper (needs 3.0) |
| `--felege-teal` | `#0E6B6E` | primary, links, active nav, focus ring | 6.27 on paper, 5.71 on mist; white on teal 6.27 |
| `--felege-teal-hover` | `#0A5457` | hover and pressed | white on it 8.68 |
| `--felege-ochre` | `#C98A1B` | FILLS and bars only, never text | 2.94 on paper (graphic only, so never the sole carrier of meaning) |
| `--felege-ochre-edge` | `#B87A0E` | outline of empty meter segments, warning button border (added: ochre is 2.94, below the 3.0 graphics minimum) | 3.60 on paper, 3.28 on mist |
| `--felege-ochre-text` | `#7A4F00` | "behind" text, warning button text | 7.13 on paper, 6.49 on mist, 6.08 on ochre tint |
| `--felege-fern` | `#3F7A4B` | paid fill, success buttons | 5.13 on paper |
| `--felege-fern-text` | `#2F5E39` | "paid" text | 7.55 on paper, 6.36 on fern tint |
| `--felege-clay` | `#A8412F` | danger, inactive | 6.07 on paper, 5.52 on mist, 5.02 on clay tint |
| `clay-hover` (Tailwind only, theme.css writes it inline) | `#8A3324` | danger button hover and pressed | white on it 8.14 |
| alert lines | teal `#B8D5D4`, fern `#BFD8C4`, ochre `#E4CC93`, clay `#E7C4BC` (Tailwind `teal-line` etc.) | 1px outline of an alert, decorative | n/a |
| tints | teal `#E3EFEE`, ochre `#F7ECD4`, fern `#E2EFE4`, clay `#F7E6E2` | badge and alert backgrounds | text on each tint is at least 5.0 |

No hex from the plan needed changing: every text pair is at least 4.5:1.
Two tokens were added (`field`, `ochre-edge`) for non-text boundaries.

No gradients anywhere. Shadows: only a very faint one on modals.

## Type

Self-hosted through npm (`@fontsource/*`, imported in `main.js`), no CDN.

| Role | Face | Weights |
|---|---|---|
| Headings, big figures | Alegreya | 700 (400 only for the words of the hero sentence), tabular lining figures where numbers align |
| Interface text | Alegreya Sans | 400, 500, 700; `tabular-nums` in tables |
| Wordmark (Ge'ez) | Noto Sans Ethiopic | 700 |

The wordmark on the landing page is a logo, outside the scale (40 to 64px, fluid). Scale: 14 / 16 / 18 / 22 / 28 / 40 px. Body is 18, labels and buttons 16, small
print 14, modal titles and h3 22, page titles 28, hero sentence 40. Headings are
sentence case. Table headers are 15px, medium weight, muted: no uppercase, no tracking.
Spacing scale: 4 / 8 / 12 / 16 / 24 / 40 / 64 (`--space-1` to `--space-7`). Radius 6px.
Table rows are 52px minimum with 1px rules.

## Signature: the woven band

An 8px strip inspired by the borders of Ethiopian tibeb cloth: ink and ochre
hairlines around a teal field with one paper diamond (ochre heart) per 8px tile.
Geometric and quiet; deliberately not the national flag colours.

- `WovenBand.vue`: inline SVG with an SVG `<pattern>`, prop `height` (default 8, the
  pattern scales with it). Decorative, so `aria-hidden`.
- Used as a quiet line at the top of the rail and of the sign-in card, and as the dues meter.

### Dues meter

`DuesMeter.vue`, props `total` (active members) and `paid` (paid up). It encodes one
thing: **one segment per active member** (capped at 40, then each segment stands for
several). The woven part is the paid share, the outlined ochre-tinted part is who still
owes. It is a picture of "who is left to call".

- Helpers in `dashboardMeter.js`: `meterSegments(total, paid, max = 40)` returns
  `{ segments, filled }`; `monthsBehind(n)` returns "1 month behind" / "2 months behind".
- `role="img"` with `aria-label="9 of 14 active members are paid up"`; the same sentence
  is printed large above it, so the meter never carries meaning alone.
- One animation in the whole app: the woven part sweeps in from the left on load
  (clip-path, 700 ms ease-out). Disabled under `prefers-reduced-motion`.
- Zero active members: no meter, an empty state instead.

## Components

Shared patterns are small Vue components in `frontend/src/components/`, built from Tailwind utilities (no custom classes).

| Component | Meaning |
|---|---|
| `StatusLabel` (`tone` paid, behind, inactive) | small dot plus the word, in fern-text, ochre-text, clay. Not a pill chip |
| `PageHead` (`title`, `lead`, slot `actions`) | page title (Alegreya 28/700) with a one-line plain description beneath; the `actions` slot holds the screen's buttons at the right (wrapping under the title on a phone) |
| `SectionTitle` | Alegreya 22/700 heading for a list or block |
| `RuledList`, `RuledRow` | rows on 1px rules, 56px tall, no boxes |
| `EmptyNote` | muted plain sentence that invites the next action |
| `TextButton` | plain teal text button for row actions ("Send reminder") |
| `BaseButton` (`variant` primary, secondary, danger; `size` sm, md, lg; `to` for a link) | the button; disabled is 65% opacity with the real `disabled` attribute |
| `BaseInput` (`id`, `label`, `v-model`, `error`, `hint`) | label, field, optional quiet hint and error text, wired with `aria-invalid` and `aria-describedby` (hint and error ids) |
| `ConfirmDialog` (`v-model`, `title`, `message`, `confirmLabel`, `danger`, `busy`; emits `confirm`) | a `BaseModal` sm that asks before an action that cannot be taken back (Messages: "Send to 8 members?"). Focus lands on Cancel. The parent does the work, shows `busy` and closes it. Use it instead of `window.confirm`; use `danger` only for something that destroys data |
| `BaseSelect` (`id`, `label`, `v-model`, `error`, `hint`; options in the slot) | the same look as `BaseInput`, for a select inside a dialog |
| `BaseTextarea` (`id`, `label`, `v-model`, `rows`, `max`, `error`, `hint`) | the same look, with a "12 of 500" counter; it reports an overrun, it does not stop typing |
| `ActionMenu` (`label`, `items` `[{ key, label, danger?, disabled? }]`, emits `select(key)`) | the row "More" menu: kebab trigger and a small paper popup (see ActionMenu rules) |
| `AlertBanner` (`tone` danger, success, warning, info) | tinted alert with a 1px line, `role="alert"` |
| `BrandMark`, `RailLink` | the wordmark link and a rail link (`aria-current="page"` on the active one) |
| `ToastHost` | renders `appStore.notifications`: paper card, 4px edge in fern, clay, ochre or teal; bottom right from sm, full width below; timers pause on hover and focus; errors are `role="alert"`, the rest `role="status"` |
| `BaseModal` (`v-model`, `title`, `size`, slots `default` and `footer`) | dialog: teleported to body, focus moves in and returns, Tab trapped, Escape and a click on the backdrop close it, page scroll locked |
| `WovenBand`, `DuesMeter` | the signature (see above) |

## ActionMenu rules

- One `ActionMenu` per row replaces a row of icon buttons. Put the common action first (Edit), the reversible state change next (Deactivate or Reactivate), the destructive one last, in clay, and only for the role that may use it (Delete is ADMIN only). A role with no allowed action sees no menu at all, not a disabled one.
- The trigger's `label` names the row: "More actions for Sarah Brown". Items are verbs in sentence case and the same word as the toast ("Deactivate" gives "Member deactivated").
- A state change that is easy to undo acts at once and toasts; an irreversible one opens a `BaseModal` that says what is lost, with the destructive button named after the action ("Delete member").
- Keyboard: Enter, Space or ArrowDown on the trigger opens and focuses the first item (ArrowUp the last); ArrowUp and ArrowDown move and wrap, Home and End jump; Escape closes and returns focus to the trigger; Tab closes; a press outside closes. Disabled items are skipped.
- The popup is teleported to `body` with fixed positioning (right edge aligned to the trigger, flipped above when there is no room below), so a menu in the last row is never clipped. It closes on scroll and resize rather than chasing the trigger. Items are 44px tall, so it works by thumb.
- Do not put more than five items in it. If a menu needs more, the screen needs a detail view.

Members, Profile, Payments and Messages are all on Tailwind; the remaining Bootstrap is listed under "T4 (remove Bootstrap)" below.

## Accessibility rules

- Every text pair at least 4.5:1 (large text 3:1); controls' edges at least 3:1.
- `:focus-visible { outline: 2px solid var(--felege-teal); outline-offset: 2px }` on everything.
- `prefers-reduced-motion`: transitions shortened, meter sweep off.
- Colour is never the only signal: status is a dot plus a word; the meter has a text sentence.
- Responsive to 360px; the rail becomes a drawer below `lg` (a Vue-state drawer: overlay, Escape and overlay click close it, focus moves to its first link and back to the menu button, `aria-expanded` and `aria-controls` on the button, closes on navigation).

## Copy rules

Plain verbs, sentence case, active voice, specific. Errors say what happened and what to do.
Empty states invite an action. One name per action everywhere ("Send reminder" is "Send reminder"
in the button and in its toast).

## Decisions

Tried and removed, so nobody puts them back:

- Four gradient stat tiles (blue, green, orange, cyan): replaced by one sentence and the meter.
  Four equal tiles said "everything matters equally"; the page's job is "who needs a call".
- Three coloured icon buttons on every Members row: replaced by one More menu (`ActionMenu`).
- Showing "N months behind" for an inactive member: the server stops counting for them, so the figure is stale. Members shows an en dash, and the Dues filter and sort treat inactive members as having no dues.
- The Profile "Edit profile" toggle with disabled fields: fields are always editable with one "Save changes" button.
- Parsing `YYYY-MM-DD` with `new Date()`: it is midnight UTC, which renders as the previous day west of UTC. `formatDate` builds a local date for date-only strings.
- Inter from Google Fonts and the icon CDN: the app now works offline and sends nothing to third parties.
- A marketing landing page with a Features grid: replaced by a quiet welcome. Nobody lands
  here who does not already have an account.
- Numbered markers and icon circles on the activity list: removed, they carried no information.
  The activity list is plain ruled rows (date, text).
- Pill chips for status: replaced by a dot plus a word.
- Client-side minimum password length on sign-in: a login form only needs a non-empty password.
- The warning-triangle icon in the sign-in error alert: the red tint and the words already say it; the icon carried nothing.
- Old coloured Profile button and input rules (scoped green `#4CAF50` in `ProfileView.vue`): removed so Profile uses the theme; its layout is now the two paper cards (done in T2).
- Tried and kept off: a woven band on the landing page. The wordmark alone is calmer, and the band stays a signature at three places only (rail, sign-in card, meter).
- The overdue list is sorted longest-overdue first and shows active members only. The "N inactive members are not shown" note was removed once the endpoint itself became active-only.
- `window.confirm` before sending a message: replaced by `ConfirmDialog`, which names the number of recipients and puts focus on Cancel.
- The Payments "this month" figure by payment date: it is by the month covered (the `period`), like the Overview, so a back-dated payment never inflates it and the two screens agree.

## Phase B checklist (remaining screens)

- [x] Members: status dot plus word in a Status column; one "More" menu per row instead of
      three coloured icon buttons; ruled table (`--row-h` rows); filters as one quiet
      row; Add member and Export as one primary and one secondary button. Done (T2).
- [x] Payments: the three stat cards become a quiet three-figure row like the Overview;
      ruled table (stacked list below `md`) with tabular amounts, right-aligned; "Record payment" as the
      one primary button, in a `BaseModal` that can back-date a payment; a receipt dialog with PDF. Done (T3).
- [x] Messages (route `/communications`, nav label and page title "Messages"): compose in a paper card
      with a confirm dialog before sending; history as a ruled list with counts as words in `StatusLabel`
      tones; a deliveries dialog with a per-row Retry. Done (T3).
- [x] Profile: forms in paper cards, one per concern (details, password); plain labels. Done (T2): the password change is a `BaseModal`.
- [x] Dialogs: titles in Alegreya 22, footers with one primary action, destructive actions in clay. Done on Members, Profile, Payments and Messages (`BaseModal`, `ConfirmDialog`).
- [x] Empty states: every list gets a plain sentence that invites the next action. Done on the Overview, Members, Payments and Messages.
- [ ] Register view: unreachable (registration is disabled); delete or keep restyled, decide (see T4 below).

**Phase B is complete: Members, Profile, Payments and Messages are done (T2 and T3).** What is left is T4, the removal of Bootstrap.

## Styling system

Tailwind CSS v4, CSS-first (`@tailwindcss/vite` in `vite.config.js`, no `tailwind.config.js`, no PostCSS config).

- **Tokens live in `frontend/src/assets/styles/tailwind.css`, inside `@theme static`.** Colours (`mist paper ink muted rule field teal teal-hover teal-tint ochre ochre-edge ochre-text ochre-tint fern fern-text fern-tint clay clay-tint`), fonts (`font-display`, `font-sans`, `font-ethiopic`), the type scale (`text-sm` 14, `base` 16, `lg` 18, `xl` 22, `2xl` 28, `3xl` 40), `rounded-md` (6px), `shadow-modal`, Bootstrap's breakpoints and the `animate-weave` meter sweep. Each family starts with `--x-*: initial`, so Tailwind's default palette and scale do not exist: only token colours can be used, by accident or otherwise. The hex values are the ones in the table above; `theme.css` keeps its own copies until T4.
- **No `@apply`, no `<style scoped>`** except where Tailwind cannot say it. Shared patterns are small Vue components (or a JS constant in the component), never a custom CSS class. Use the animation as `motion-safe:animate-weave`.
- **Every Tailwind class has the prefix `tw:`** (`tw:flex tw:gap-3 tw:md:flex tw:motion-safe:animate-weave`; the prefix comes before any variant). Reason: Bootstrap's own utility classes (`p-3`, `gap-3`, `border`, `m-0`, `text-end`) are `!important` and share names with Tailwind's but not values, and no cascade order can serve both the migrated and the unmigrated screens. Theme variables carry it too (`var(--tw-color-teal)`), which is why the theme is `@theme static` (all tokens are always emitted). The density variables are not Tailwind's and have no prefix (`tw:h-(--control-h)`). T4 removes the prefix with a mechanical search and replace of `tw:`.
- **Bootstrap stays until T4.** No routed screen uses a Bootstrap class or its JavaScript any more (only the unrouted `RegisterView.vue` does); the CSS stays only because T4 has not removed it yet. Bootstrap's CSS and `theme.css` are imported by `tailwind.css` into one `legacy` cascade layer (one layer, so their `!important` rules keep fighting in file order), below Tailwind's `utilities` layer. This is needed because unlayered CSS beats every layer: a Bootstrap `h1` or `a` rule would otherwise beat a utility on a migrated screen. Tailwind runs WITHOUT its global reset (`preflight`) so the legacy layer keeps the page as it was; T4 adds it, and drops the `legacy` layer.
- **Migration order:** T1 Tailwind foundation, toast and dialog components, shell, Overview, landing, sign-in (done). T2 Members and Profile (done). T3 Payments, Messages, the Overview leftovers (done). T4 remove Bootstrap (CSS, JS, `theme.css`, the `legacy` layer) and add preflight (next).

## Density

Two densities share one palette and one type family. They are plain CSS custom properties in `tailwind.css`, switched by a `data-density` attribute (not `@theme`: they change at runtime). Utilities read them with `tw:h-(--control-h)`, `tw:min-h-(--row-h)`, `tw:p-(--card-pad)`, `tw:text-(length:--text-body)`.

| Property | dense (default) | comfortable |
|---|---|---|
| `--control-h` | 36px | 44px |
| `--control-primary-h` | 36px | 48px |
| `--row-h` | 44px | 56px |
| `--list-row-h` | 48px | 56px |
| `--card-pad` | 16px | 16px (24px from 768px) |
| `--text-body` / `--lh-body` | 16px / 22px | 18px / 26px |
| `--text-label` / `--lh-label` | 14px / 18px | 15px / 20px |
| `--text-title` / `--lh-title` | 18px / 24px | 20px / 26px |

- **Dense:** staff screens (Overview, Members, Payments, Messages). **Comfortable:** guests (landing, sign-in) and a MEMBER's Profile (which follows `data-density`, so staff see the same page dense).
- `App.vue` sets `data-density` on the shell root: `comfortable` when signed out or when the role is MEMBER, otherwise `dense`. `LandingView` and `LoginView` render outside the rail shell and set `data-density="comfortable"` on their own root.
- Inputs never go below 16px text (iOS zooms the page on smaller). The primary action on a comfortable screen is 48px and full width on mobile.
- Status, as before, is a dot plus a word, never plain coloured text. Every list has an empty state that names the next step. A switch's whole row is the click target (wrap it in a label). Disabled means a light fill, muted text and the real `disabled` attribute.
- T1 defined and plumbed the tokens. The Overview keeps its own sizes (it must look exactly as before); Members, Payments and Messages consume them (`tw:h-(--control-h)` for the filter controls, `tw:min-h-(--row-h)` for table rows, `tw:text-(length:--text-body)`). Dialogs are teleported to `body`, outside `data-density`, so their controls keep fixed sizes (`BaseInput`, `BaseSelect`, `BaseTextarea`).

## Dialog rules

- Every dialog is a `BaseModal`: Alegreya 22 title, the close button, one primary action in the footer, `Cancel` beside it. Never `window.confirm`, `alert` or Bootstrap's `Modal`.
- **`ConfirmDialog` is for an action that cannot be taken back or that reaches other people** (sending email to members, deleting). Props: `v-model`, `title` (a question that names the amount: "Send to 8 members?"), `message` (what happens, and that it cannot be undone), `confirmLabel` (the same verb phrase as the question: "Send to 8 members"), `danger` (clay button, only for destroying data), `busy` (disables both buttons while the parent works). It emits `confirm`; the parent does the work and closes it with `v-model` when done. Focus lands on Cancel, so a stray Enter never confirms.
- An action that is easy to undo does not get a dialog: it acts at once and toasts ("Member deactivated").
- Forms in a dialog show field errors under their field and any other server message in an `AlertBanner` at the top; a failed save keeps the dialog open; success closes it, resets the form and toasts with the same name as the button ("Record payment" gives "Payment recorded").

## T4 (remove Bootstrap)

Phase B is finished, so what is left is mechanical. What still uses Bootstrap:

| Where | What |
|---|---|
| `frontend/src/main.js` | `import 'bootstrap'` (the JavaScript, nothing calls it any more); `bootstrap-icons` stays: icons are `bi bi-*` |
| `frontend/package.json` | the `bootstrap` dependency (keep `bootstrap-icons`); `@popperjs/core` was only Bootstrap's peer: check and drop it too |
| `frontend/src/assets/styles/tailwind.css` | `@layer legacy`, `@import "bootstrap/dist/css/bootstrap.min.css" layer(legacy)` and `@import "./theme.css" layer(legacy)`; no preflight yet |
| `frontend/src/assets/styles/theme.css` | the legacy overrides (about 417 lines). Port four things before deleting it: the `body` rule (Alegreya Sans, 18px, mist background, ink text, `-webkit-font-smoothing: antialiased`), the heading rule (Alegreya 700, line height 1.2, sizes 40/28/22/18), `a { text-underline-offset: 3px }` and `::selection` (teal on paper). Components already carry their own sizes and colours |
| `frontend/src/views/RegisterView.vue` | unrouted (registration is disabled) and still all Bootstrap (`container`, `form-control`, `btn`, `alert`, `spinner-border`, scoped CSS). Decide: delete it (it has no route and nothing imports it) or rebuild it on `BaseInput` and `BaseButton` |
| `frontend/src/App.vue` | `themeClass` returns the string `data-bs-theme="..."` and puts it in `class`, which does nothing; remove it and `appStore.currentTheme` if nothing else reads it |
| `frontend/src/utils/index.js` | the `showToast` doc comment still says "Bootstrap toast" |
| leftovers | `grep -rnE '(class|:class)=' src` for Bootstrap names (`btn`, `card`, `form-*`, `row`, `col-*`, `d-flex`, `me-*`, `badge`, `alert`, `modal`, `table`, `spinner-border`, `text-muted`) and for `data-bs-` |

Plan to drop the `tw:` prefix and Bootstrap, in this order, one commit each, `npm test` and `npm run build` after every step:

1. Delete `RegisterView.vue` (or rebuild it), `themeClass`, `import 'bootstrap'`, then `npm uninstall bootstrap`. Check no screen changed: the legacy layer is still there.
2. Port the four base rules above into `@layer base` in `tailwind.css`, remove the `theme.css` import, delete `theme.css`, drop the Bootstrap CSS import and `@layer legacy`. Switch the header to `@layer theme, base, components, utilities;` and import the whole of Tailwind with preflight (`@import "tailwindcss";`) while the prefix is still on. Walk every screen at 1280 and 390: preflight resets margins, list styles, heading sizes and borders, so look for a lost margin or a border that fell back to `currentColor`.
3. Drop the prefix: remove `prefix(tw)` from the imports; run a search and replace over `frontend/src` for the literal `tw:` in `.vue` and `.js` files (it only ever appears at the start of a class or after a space or quote: `tw:flex`, `tw:md:gap-3`, `tw:[overflow-wrap:anywhere]`, `tw:h-(--control-h)`; the prefix always comes before the variants, so deleting it leaves valid classes). Also change `var(--tw-color-teal)` in `tailwind.css` to `var(--color-teal)` (theme variables lose the prefix; this is the only `var(--tw-...)` in the source today). Update the one test that asserts a class name (`frontend/src/__tests__/components/ConfirmDialog.test.js`, `tw:bg-clay`). Search the docs for examples that show `tw:` (this file and the feature docs).
4. Re-run the full walk (Overview, Members with the row menu and dialogs, Payments with the record and receipt dialogs and the PDF, Messages with the confirm and deliveries dialogs, Profile and its dialog, landing, sign-in) at 1280 and 390, plus the keyboard checks, and compare with the T3 screenshots.
5. Update this file: remove the Bootstrap notes under "Styling system", the `tw:` rule, and this section.

