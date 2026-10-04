// Wording for the dues figures. `Recipients` and the overdue queries own the rules; this is only how
// the overdue count reads on the Overview and in the member list.

/** "1 month behind", "2 months behind". */
export function monthsBehind(n) {
  return `${n} ${n === 1 ? 'month' : 'months'} behind`
}

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

const monthName = (key) => MONTH_NAMES[Number(key.slice(5)) - 1]

/** "July, August and September 2026"; a list that crosses a year names the year on every month. */
function monthList(months) {
  const years = new Set(months.map(key => key.slice(0, 4)))
  const names = years.size === 1 ? months.map(monthName) : months.map(key => `${monthName(key)} ${key.slice(0, 4)}`)
  const list = names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`
  return years.size === 1 ? `${list} ${[...years][0]}` : list
}

/** "July 2026" from "2026-07". */
export function longMonth(key) {
  return `${monthName(key)} ${key.slice(0, 4)}`
}

/**
 * What a member owes, read off the year strip cells (utils/yearStrip.js stripCells): the months
 * the server counts as missed, plus the current month when it is still unpaid.
 * @returns {{ oldest: string, sentence: string }} oldest is the "yyyy-MM" to record first, '' when paid up
 */
export function owedSummary(cells) {
  const missed = cells.filter(cell => cell.state === 'missed').map(cell => cell.month)
  const due = cells.find(cell => cell.state === 'due')
  const current = cells[cells.length - 1]
  const dueText = due ? `${monthName(due.month)} is due now.` : ''
  let sentence
  if (missed.length) sentence = `Owes ${monthList(missed)}.${dueText ? ` ${dueText}` : ''}`
  else if (due) sentence = `Paid up to last month. ${dueText}`
  else sentence = `Paid up. Nothing is owed for ${longMonth(current.month)}.`
  return { oldest: missed[0] || due?.month || '', sentence }
}
