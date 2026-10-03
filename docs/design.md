# Design notes: Felege Selam

Subject: Felege Selam Church (ፈለገ ሰላም, Amharic for "stream of peace"). The
app has one job: show who needs a call, record payments, reach members kindly.
Users are the treasurer and volunteers who open it weekly after services (dense,
repeat use), and members who only see their own profile.

Code: `frontend/src/assets/styles/theme.css` (tokens and Bootstrap overrides),
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

## Component classes

| Class | Meaning |
|---|---|
| `.status`, `.status--paid`, `.status--behind`, `.status--inactive` | small dot plus the word, in fern-text, ochre-text, clay. Not a pill chip |
| `.figure-display` | Alegreya 700, tabular lining figures |
| `.page-head`, `.page-title`, `.page-lead` | page title (Alegreya 28/700) with a one-line plain description beneath |
| `.section-title` | Alegreya 22/700 heading for a list or block |
| `.ruled-list`, `.ruled-list__row`, `__main`, `__date`, `__amount` | rows on 1px rules, 56px tall, no boxes |
| `.empty-note` | muted plain sentence that invites the next action |
| `.text-action` | plain teal text button for row actions ("Send reminder") |
| `.rail`, `.rail__link`, `.rail__user`, `.topbar` | app shell in `App.vue`: 248px left rail from lg, slim top bar plus offcanvas below |
| `.toast-note`, `--success`, `--error`, `--warning` | paper toast with a 4px colour edge |
| `.btn-warning` | ochre text on an ochre tint (secondary), not white on orange |
| `.badge.bg-*` | quiet tinted text, kept only until phase B replaces badges with `.status` |

## Accessibility rules

- Every text pair at least 4.5:1 (large text 3:1); controls' edges at least 3:1.
- `:focus-visible { outline: 2px solid var(--felege-teal); outline-offset: 2px }` on everything.
- `prefers-reduced-motion`: transitions shortened, meter sweep off.
- Colour is never the only signal: status is a dot plus a word; the meter has a text sentence.
- Responsive to 360px; the rail becomes an offcanvas menu below `lg`.

## Copy rules

Plain verbs, sentence case, active voice, specific. Errors say what happened and what to do.
Empty states invite an action. One name per action everywhere ("Send reminder" is "Send reminder"
in the button and in its toast).

## Decisions

Tried and removed, so nobody puts them back:

- Four gradient stat tiles (blue, green, orange, cyan): replaced by one sentence and the meter.
  Four equal tiles said "everything matters equally"; the page's job is "who needs a call".
- Three coloured icon buttons on every row: not yet replaced on Members (phase B, one More menu).
- Inter from Google Fonts and the icon CDN: the app now works offline and sends nothing to third parties.
- A marketing landing page with a Features grid: replaced by a quiet welcome. Nobody lands
  here who does not already have an account.
- Numbered markers and icon circles on the activity list: removed, they carried no information.
  The activity list is plain ruled rows (date, text).
- Pill chips for status: replaced by a dot plus a word.
- Client-side minimum password length on sign-in: a login form only needs a non-empty password.
- The warning-triangle icon in the sign-in error alert: the red tint and the words already say it; the icon carried nothing.
- Old coloured Profile button and input rules (scoped green `#4CAF50` in `ProfileView.vue`): removed so Profile uses the theme; its layout is phase B.
- Tried and kept off: a woven band on the landing page. The wordmark alone is calmer, and the band stays a signature at three places only (rail, sign-in card, meter).
- The overdue list is sorted longest-overdue first (the API order is arbitrary) and shows active members only, with a count note for inactive ones.

## Phase B checklist (remaining screens)

- [ ] Members: status dot plus word in a Status column; one "More" menu per row instead of
      three coloured icon buttons; ruled, airy table (52 to 56px rows); filters as one quiet
      row; Add member and Export as one primary and one secondary button.
- [ ] Payments: the three stat cards become a quiet two-or-three-figure row like the Overview;
      table with tabular amounts, right-aligned; Record payment as the one primary button.
- [ ] Messages (route `/communications`, nav label "Messages"): compose in a paper card;
      delivery results as a ruled list with `.status`; page title still says Communications, align it.
- [ ] Profile: forms in paper cards, one per concern (details, password); plain labels.
- [ ] Dialogs: titles in Alegreya 22, footers with one primary action, destructive actions in clay.
- [ ] Empty states: every list gets a plain sentence that invites the next action.
- [ ] Register view: unreachable (registration is disabled); delete or keep restyled, decide.

## Styling system

Tailwind CSS v4, CSS-first (`@tailwindcss/vite` in `vite.config.js`, no `tailwind.config.js`, no PostCSS config).

- **Tokens live in `frontend/src/assets/styles/tailwind.css`, inside `@theme static`.** Colours (`mist paper ink muted rule field teal teal-hover teal-tint ochre ochre-edge ochre-text ochre-tint fern fern-text fern-tint clay clay-tint`), fonts (`font-display`, `font-sans`, `font-ethiopic`), the type scale (`text-sm` 14, `base` 16, `lg` 18, `xl` 22, `2xl` 28, `3xl` 40), `rounded-md` (6px), `shadow-modal`, Bootstrap's breakpoints and the `animate-weave` meter sweep. Each family starts with `--x-*: initial`, so Tailwind's default palette and scale do not exist: only token colours can be used, by accident or otherwise. The hex values are the ones in the table above; `theme.css` keeps its own copies until T4.
- **No `@apply`, no `<style scoped>`** except where Tailwind cannot say it. Shared patterns are small Vue components (or a JS constant in the component), never a custom CSS class. Use the animation as `motion-safe:animate-weave`.
- **Every Tailwind class has the prefix `tw:`** (`tw:flex tw:gap-3 tw:md:flex tw:motion-safe:animate-weave`; the prefix comes before any variant). Reason: Bootstrap's own utility classes (`p-3`, `gap-3`, `border`, `m-0`, `text-end`) are `!important` and share names with Tailwind's but not values, and no cascade order can serve both the migrated and the unmigrated screens. Theme variables carry it too (`var(--tw-color-teal)`), which is why the theme is `@theme static` (all tokens are always emitted). The density variables are not Tailwind's and have no prefix (`tw:h-(--control-h)`). T4 removes the prefix with a mechanical search and replace of `tw:`.
- **Bootstrap stays until T4.** Members, Payments, Messages and Profile still use Bootstrap classes, tables and modals; they migrate in T2 and T3. Until then Bootstrap's CSS and `theme.css` are imported by `tailwind.css` into one `legacy` cascade layer (one layer, so their `!important` rules keep fighting in file order), below Tailwind's `utilities` layer. This is needed because unlayered CSS beats every layer: a Bootstrap `h1` or `a` rule would otherwise beat a utility on a migrated screen. Tailwind runs WITHOUT its global reset (`preflight`) so the unmigrated screens do not change; T4 adds it, and drops the `legacy` layer.
- **Migration order:** T1 Tailwind foundation, toast and dialog components, shell, Overview, landing, sign-in. T2 Members and Payments. T3 Messages and Profile. T4 remove Bootstrap (CSS, JS, `theme.css`, the `legacy` layer) and add preflight.

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

- **Dense:** staff screens (Overview, Members, Payments, Messages). **Comfortable:** guests (landing, sign-in) and a MEMBER's Profile.
- `App.vue` sets `data-density` on the shell root: `comfortable` when signed out or when the role is MEMBER, otherwise `dense`. `LandingView` and `LoginView` render outside the rail shell and set `data-density="comfortable"` on their own root.
- Inputs never go below 16px text (iOS zooms the page on smaller). The primary action on a comfortable screen is 48px and full width on mobile.
- Status, as before, is a dot plus a word, never plain coloured text. Every list has an empty state that names the next step. A switch's whole row is the click target (wrap it in a label). Disabled means a light fill, muted text and the real `disabled` attribute.
- T1 defines and plumbs the tokens only. The Overview keeps its own sizes (it must look exactly as before); T2 and T3 consume the tokens.

## Tailwind migration (next)

The frontend is moving to Tailwind CSS. What stays: the tokens (names and hex values), the
self-hosted fonts, `WovenBand`, `DuesMeter`, the `dashboardMeter` helpers, the copy rules and the
layout decisions (left rail, 1100px content area, hero sentence plus meter, ruled lists, status dot
plus word). What goes: the Bootstrap class markup and the Bootstrap-variable overrides in
`theme.css`, replaced by Tailwind v4 utilities with CSS-first `@theme` tokens in phases T1 to T4,
done by other agents. Bootstrap's JS (modal, toast, offcanvas) needs replacements at the same time.
