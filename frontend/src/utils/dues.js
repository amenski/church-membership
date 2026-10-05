// Wording for the dues figures. `Recipients` and the overdue queries own the rules; this is only how
// the overdue count reads on the Overview and in the member list.
//
// Every function that produces a sentence takes the `t` of useI18n(): the words live in locales/,
// the rules stay here. Month names come from formatDate, which already follows the UI language.
import { countsForDues } from '@/utils/memberStatus'
import { stripCells } from '@/utils/yearStrip'
import { formatDate } from '@/utils'

/** "1 month behind", "2 months behind". */
export function monthsBehind(n, t) {
  return t('dues.monthsBehind', n)
}

/** "July" from "2026-07". */
export const monthName = (key) => formatDate(`${key}-01`, 'MMMM')

/** "July 2026" from "2026-07". */
export function longMonth(key) {
  return formatDate(`${key}-01`, 'MMMM yyyy')
}

/** "July, August and September"; the conjunction and the comma are the language's. */
function nameList(names, t) {
  if (names.length === 1) return names[0]
  const items = names.slice(0, -1).join(t('common.listSeparator'))
  return t('dues.listAnd', { items, last: names[names.length - 1] })
}

/** "July, August and September 2026"; a list that crosses a year names the year on every month. */
function monthList(months, t) {
  const years = new Set(months.map(key => key.slice(0, 4)))
  if (years.size === 1) return t('strip.monthAndYear', { month: nameList(months.map(monthName), t), year: months[0].slice(0, 4) })
  return nameList(months.map(longMonth), t)
}

/**
 * What a member owes, read off the year strip cells (utils/yearStrip.js stripCells): the months
 * the server counts as missed, plus the current month when it is still unpaid.
 * @param {object[]} cells
 * @param {Function} t useI18n's t
 * @returns {{ oldest: string, sentence: string }} oldest is the "yyyy-MM" to record first, '' when paid up
 */
export function owedSummary(cells, t) {
  const missed = cells.filter(cell => cell.state === 'missed').map(cell => cell.month)
  const due = cells.find(cell => cell.state === 'due')
  const current = cells[cells.length - 1]
  const dueText = due ? t('dues.dueNow', { month: monthName(due.month) }) : ''
  let sentence
  if (missed.length) {
    const owes = t('dues.owes', { months: monthList(missed, t) })
    sentence = dueText ? `${owes} ${dueText}` : owes
  } else if (due) sentence = `${t('dues.paidUpToLastMonth')} ${dueText}`
  else sentence = t('dues.paidUp', { month: longMonth(current.month) })
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
 * @param {Function} t useI18n's t
 * @returns {{ tone: 'danger'|'behind'|'paid', text: string }|null} tone is a StatusBadge tone
 */
export function duesBadge(member, paidByMember, currentMonth, t) {
  if (!countsForDues(member)) return null
  const behind = member.consecutiveMonthsMissed || 0
  if (behind > 0) return { tone: 'danger', text: monthsBehind(behind, t) }
  if (!paidByMember || !currentMonth) return null
  const [current] = stripCells({ currentMonth, joinDate: member.joinDate || '', paidMonths: paidByMember.get(member.id) || new Set(), monthsMissed: 0, countsForDues: true, count: 1 })
  if (current.state === 'due') return { tone: 'behind', text: t('dues.badgeDue') }
  return current.state === 'paid' ? { tone: 'paid', text: t('dues.badgePaid') } : null
}
