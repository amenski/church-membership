// Shared Tailwind class strings for the admin screens. Before this file every view redeclared
// its own LABEL / CONTROL / TH / TD / TABLE and they had quietly diverged (Activity's control
// dropped the placeholder colour, Profile's label used a different size). One source, imported
// by the views, so a table or a filter looks the same everywhere.

/** The label above a form control. */
export const LABEL = 'mb-1 block text-xs font-medium text-muted'

/** A text/date/select control at the density height. 16px text: below that iOS zooms the page. */
export const CONTROL = 'block h-(--control-h) w-full rounded-sm border border-field bg-paper px-2.5 text-lg text-ink placeholder:text-muted focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal'

/** The desktop table, its header cells and its body cells (the rows carry the rules). */
export const TABLE = 'hidden w-full border-collapse text-left text-base tabular-nums md:table'
/** The same table for a screen whose stacked cards stay up to `lg` (Members: the rail leaves under 760px of page at 992px). */
export const TABLE_FROM_LG = 'hidden w-full border-collapse text-left text-base tabular-nums lg:table'
export const TABLE_TH ='pb-2 pr-3 text-xs font-medium text-muted first:pl-0 last:pr-0'
export const TABLE_TD = 'py-0 pr-3 align-middle first:pl-0 last:pr-0'
/** The header and body cells of a table inside a bordered card (Members): grey header row, 16px at both edges, 10px above and below. */
export const TABLE_CARD_TH = 'bg-mist px-3 py-2.5 text-xs font-medium text-muted first:pl-4 last:pr-4'
export const TABLE_CARD_TD = 'px-3 py-2.5 align-middle first:pl-4 last:pr-4'

/** A clickable column header. */
export const SORT_BUTTON = '-mx-1 inline-flex cursor-pointer items-center gap-1 rounded-sm border-0 bg-transparent px-1 py-0.5 text-xs font-medium text-muted hover:text-ink'

/** The first cell of a row: the row's name. */
export const NAME = 'font-medium text-ink [overflow-wrap:anywhere]'

/** A surface: dialog body, compose card, profile section. */
export const CARD = 'rounded-md border border-rule bg-paper p-(--card-pad)'

/** A number that should align with the numbers above and below it. */
export const FIGURE = 'font-semibold tabular-nums'
