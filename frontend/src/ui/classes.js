// Shared Tailwind class strings for the admin screens. Before this file every view redeclared
// its own LABEL / CONTROL / TH / TD / TABLE and they had quietly diverged (Activity's control
// dropped the placeholder colour, Profile's label used a different size). One source, imported
// by the views, so a table or a filter looks the same everywhere.

/** The label above a form control. */
export const LABEL = 'mb-1 block text-xs font-medium text-muted'

/** A text/date/select control at the density height. */
export const CONTROL = 'block h-(--control-h) w-full rounded-sm border border-field bg-paper px-2.5 text-base text-ink placeholder:text-muted focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal'

/** The desktop table and its cells. */
export const TABLE = 'hidden w-full border-collapse text-left md:table'
export const TABLE_TH = 'border-b border-rule pb-2 pr-4 text-xs font-medium text-muted'
export const TABLE_TD = 'border-b border-rule py-0 pr-4 align-middle'

/** A clickable column header. */
export const SORT_BUTTON = 'inline-flex cursor-pointer items-center gap-1 border-0 bg-transparent p-0 text-xs font-medium text-muted hover:text-ink'

/** The first cell of a row: the row's name. */
export const NAME = 'font-medium text-ink'

/** A surface: dialog body, compose card, profile section. */
export const CARD = 'rounded-md border border-rule bg-paper p-(--card-pad)'

/** A number that should align with the numbers above and below it. */
export const FIGURE = 'font-semibold tabular-nums'
