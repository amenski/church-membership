// date-fns 4 ships no Amharic locale — node_modules/date-fns/locale has no `am` — so this builds the
// one thing the UI actually shows: the names. Every call site formats with an explicit pattern
// ('MMM dd, yyyy', 'EEEE, d MMMM yyyy') and never with a P/PX token, so month and day are the only
// parts of the locale that matter; the rest comes from en-US and is never reached.
//
// The names are ICU's (Intl.DateTimeFormat('am')), not hand-written, so they match what the rest of
// the platform would render. Months are the Gregorian ones.
import { enUS } from 'date-fns/locale'

const MONTHS = {
  narrow: ['ጃ', 'ፌ', 'ማ', 'ኤ', 'ሜ', 'ጁ', 'ጁ', 'ኦ', 'ሴ', 'ኦ', 'ኖ', 'ዲ'],
  abbreviated: ['ጃን', 'ፌብ', 'ማርች', 'ኤፕሪ', 'ሜይ', 'ጁን', 'ጁላይ', 'ኦገስ', 'ሴፕቴ', 'ኦክቶ', 'ኖቬም', 'ዲሴም'],
  wide: ['ጃንዋሪ', 'ፌብሩዋሪ', 'ማርች', 'ኤፕሪል', 'ሜይ', 'ጁን', 'ጁላይ', 'ኦገስት', 'ሴፕቴምበር', 'ኦክቶበር', 'ኖቬምበር', 'ዲሴምበር']
}

const DAYS = {
  narrow: ['እ', 'ሰ', 'ማ', 'ረ', 'ሐ', 'ዓ', 'ቅ'],
  short: ['እሑድ', 'ሰኞ', 'ማክሰ', 'ረቡዕ', 'ሐሙስ', 'ዓርብ', 'ቅዳሜ'],
  abbreviated: ['እሑድ', 'ሰኞ', 'ማክሰ', 'ረቡዕ', 'ሐሙስ', 'ዓርብ', 'ቅዳሜ'],
  wide: ['እሑድ', 'ሰኞ', 'ማክሰኞ', 'ረቡዕ', 'ሐሙስ', 'ዓርብ', 'ቅዳሜ']
}

// A width date-fns asks for that the language does not distinguish falls back to the full name
const pick = (table, index, width) => table[width]?.[index] ?? table.wide[index]

export const am = {
  ...enUS,
  code: 'am',
  localize: {
    ...enUS.localize,
    month: (index, options = {}) => pick(MONTHS, index, options.width),
    day: (index, options = {}) => pick(DAYS, index, options.width)
  }
}
