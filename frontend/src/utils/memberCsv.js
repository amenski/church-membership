// A CSV of the given members, in the layout of the server export (MemberController.csvResponse):
// the same header row, ISO join date, the stored months behind and the status word. Used for
// "Export selected" on the Members screen, which needs no round trip.

const HEADER = 'ID,Name,Email,Phone,Join date,Months behind,Status'
const PHONE_OR_NUMBER = /^[+-]?[0-9 ().-]+$/

// Same rule as CsvUtils.escapeCsv: a leading = + - @ tab or CR gets a ' so a spreadsheet never runs
// it as a formula (a phone number or plain number is left alone), then quote when needed.
export function escapeCsv(value) {
  if (value === null || value === undefined) return ''
  let safe = String(value)
  if (safe && '=+-@\t\r'.includes(safe[0]) && !PHONE_OR_NUMBER.test(safe)) safe = `'${safe}`
  if (/[",\r\n]/.test(safe)) return `"${safe.replace(/"/g, '""')}"`
  return safe
}

/** UTF-8 byte order mark first, as the server does, so Excel reads non-Latin names (Amharic). */
export function membersCsv(members) {
  const rows = members.map(member => [
    member.id ?? '0',
    escapeCsv(member.name),
    escapeCsv(member.email),
    escapeCsv(member.phone),
    member.joinDate ? String(member.joinDate).slice(0, 10) : '',
    member.consecutiveMonthsMissed ?? 0,
    member.status
  ].join(','))
  return `﻿${[HEADER, ...rows].join('\n')}\n`
}
