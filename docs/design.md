# Design notes: Felege Selam

Subject: Felege Selam Church (ፈለገ ሰላም, Amharic for "stream of peace"). The app is an internal
admin dashboard: the treasurer and volunteers open it weekly after services to see who needs a
call, record payments and reach members; a signed-in member sees only their own profile.

Code: `frontend/src/assets/styles/tailwind.css` (the only stylesheet: tokens and base rules),
the components in `frontend/src/components/`, and the shared class strings in
`frontend/src/ui/classes.js`.

## Look

A restrained, dense admin surface: neutral greys, one accent (teal) and three status colours used
only for status. No gradients, no decorative illustration, no serif display face, no animation
beyond the spinner. Structure carries the meaning: a compact top bar, a persistent left rail, a
rule under every page header, and tables for anything that repeats.

## Tokens

Each is a `--color-*` variable in the `@theme` block of `tailwind.css` (`--color-mist` is
`bg-mist`, `text-mist`, `border-mist`). Every default Tailwind palette and scale is zeroed first
(`--color-*: initial`), so nothing outside this list exists.

| Token | Hex | Use | Contrast (checked with the WCAG 2.x formula) |
|---|---|---|---|
| `--color-mist` | `#F6F7F9` | page background | ink on mist 16.55 |
| `--color-paper` | `#FFFFFF` | surfaces: rail, tables, cards, dialogs | ink on paper 17.74 |
| `--color-ink` | `#111827` | body text, headings | see above |
| `--color-muted` | `#4B5563` | secondary text, table headers, labels | 7.56 on paper, 7.05 on mist |
| `--color-rule` | `#E5E7EB` | 1px rules and card borders (decorative) | n/a |
| `--color-field` | `#6B7280` | form-control borders | 4.83 on paper, 4.51 on mist (needs 3.0) |
| `--color-teal` | `#0F766E` | the accent: primary, links, active nav, focus ring | 5.47 on paper, 5.11 on mist; white on teal 5.47 |
| `--color-teal-hover` | `#115E59` | hover and pressed | white on it 7.58 |
| `--color-teal-tint` | `#F0FDFA` | active nav and hover background | teal on it 5.25 |
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

Inter (npm `@fontsource/inter`, weights 400/500/600), self-hosted, no CDN. The Amharic wordmark
ፈለገ ሰላም uses Noto Sans Ethiopic (700) and is the only place that family appears.

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

- **Dense:** every staff screen. **Comfortable:** sign-in, and a MEMBER's own profile.
- `App.vue` sets `data-density` on the shell root: `comfortable` when signed out or when the role
  is MEMBER, otherwise `dense`.
- `BaseButton` consumes the density height: `primary` and `danger` hold `--control-primary-h`,
  `secondary` `--control-h`, and `size="sm"` stays padding-sized. `min-height` never shrinks a
  button, so a dense screen is unaffected by the comfortable values.
- Dialogs are teleported to `body`, outside `data-density`, so their controls keep the dense
  (root) height at any density.

## Layout

- **Top bar** (`App.vue`): sticky, 48px, a breadcrumb on the left (each route declares
  `meta.title`; it falls back to nothing) and the account plus Sign out on the right. Below `lg`
  the same bar carries the brand and the menu button that opens the rail.
- **Rail**: fixed, 248px, below the top bar from `lg` up; a drawer below `lg` with a focus trap, an
  inert rail when closed, Escape to close and close-on-navigate. Navigation only; the account
  block lives in the top bar.
- **Content**: offset by the rail, centred, `max-w-[1400px]`, 24px padding.
- **Page header**: `PageHead` (title, optional lead, action slot) with a rule under it.
- Tables repeat the same shape: `ui/classes.js` `TABLE` / `TABLE_TH` / `TABLE_TD`, rows at
  `--row-h`, with a stacked card list below `md` for the four list screens.

## Components

`PageHead`, `SectionTitle`, `BaseButton`, `BaseInput`, `BaseSelect`, `BaseTextarea`, `BaseModal`,
`ConfirmDialog`, `ActionMenu`, `AlertBanner`, `EmptyNote`, `StatusLabel`, `TextButton`, `StatTile`,
`Icon`, `RuledList`/`RuledRow`, `BrandMark`, `RailLink`, `ToastHost`.

- `Icon` holds the whole icon set as inline SVG path data — no icon font, and only the glyphs named
  reach the bundle.
- `StatTile` is the one place a large figure is styled; it sits inside a `<dl>`.
- `StatusLabel` is a coloured dot **plus a word**, never colour alone. Tones: `paid` (fern),
  `behind` (ochre), `danger` (clay), `muted` (neutral — inactive, transferred, deceased, archived).

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
| 2026-10 | `bootstrap-icons` was replaced by an inline-SVG `Icon` component | One icon font shipped 314 KB for ~15 glyphs | In use |
| 2026-10 | The two-density tokens were kept rather than collapsed to one scale | Sign-in and a MEMBER's profile are still comfortable, and the tokens are the lever for that | In use |
