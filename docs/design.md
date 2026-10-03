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
| Headings, big figures | Alegreya | 700, tabular lining figures where numbers align |
| Interface text | Alegreya Sans | 400, 500, 700; `tabular-nums` in tables |
| Wordmark (Ge'ez) | Noto Sans Ethiopic | 700 |

Scale: 14 / 16 / 18 / 22 / 28 / 40 px. Body is 18, labels and buttons 16, small
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
