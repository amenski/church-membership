// Wording for the dues figures. `Recipients` and the overdue queries own the rules; this is only how
// the overdue count reads on the Overview and in the member list.
import { countsForDues } from '@/utils/memberStatus'
import { stripCells } from '@/utils/yearStrip'

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

/**
 * The one-word dues state of a member, for a row that has no room for the year strip (the member
 * picker). Behind is the server's count, as everywhere; "due this month" and "paid up" read the
 * current month off the year strip rule, so they need the paid months. Without them only "behind"
 * shows. A member who owes no dues has no badge.
 * @param {object} member
 * @param {Map<number, Set<string>>|null} paidByMember memberId -> paid "yyyy-MM" months (yearStrip.js paidMonthsFromMap)
 * @param {string} currentMonth "yyyy-MM"
 * @returns {{ tone: 'danger'|'behind'|'paid', text: string }|null} tone is a StatusBadge tone
 */
export function duesBadge(member, paidByMember, currentMonth) {
  if (!countsForDues(member)) return null
  const behind = member.consecutiveMonthsMissed || 0
  if (behind > 0) return { tone: 'danger', text: monthsBehind(behind) }
  if (!paidByMember || !currentMonth) return null
  const [current] = stripCells({ currentMonth, joinDate: member.joinDate || '', paidMonths: paidByMember.get(member.id) || new Set(), monthsMissed: 0, countsForDues: true, count: 1 })
  if (current.state === 'due') return { tone: 'behind', text: 'Due this month' }
  return current.state === 'paid' ? { tone: 'paid', text: 'Paid up' } : null
}
