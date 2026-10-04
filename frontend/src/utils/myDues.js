// Wording for the member's own screen (MyDuesView). The rule is the one the year strip and the member list use:
// the server's monthsBehind counts the missed months, and a current month that is unpaid is "due now", not late.
import { longMonth, monthsBehind, owedSummary } from '@/utils/dues'
import { stripCells } from '@/utils/yearStrip'
import { statusLabel } from '@/utils/memberStatus'
import { formatDate, formatMoney } from '@/utils'
import { methodLabel } from '@/utils/paymentHistory'

const shiftMonth = (key, by) => {
  const index = Number(key.slice(0, 4)) * 12 + (Number(key.slice(5)) - 1) + by
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

/** "member since January 2023", or '' when the join date is missing. */
export function memberSince(joinDate) {
  return joinDate ? `Member since ${formatDate(joinDate, 'MMMM yyyy')}` : ''
}

/**
 * The status card: a headline and a sentence under it.
 * @param {object} dues the GET /api/me/dues body
 * @param {string} currentMonth "yyyy-MM"
 * @returns {{ title: string, detail: string }}
 */
export function myDuesStatus(dues, currentMonth) {
  const paid = new Set(dues.paidMonths || [])
  if (dues.status !== 'MEMBER') {
    return { title: `Your membership is ${statusLabel(dues.status).toLowerCase()}`, detail: 'No dues are counted while your membership is not active.' }
  }
  const last = dues.payments?.[0]
  const lastPayment = last ? `Your last payment was ${formatMoney(last.amount)} by ${methodLabel(last.method).toLowerCase()} on ${formatDate(last.paymentDate, 'MMMM d')}.` : ''
  const currentPaid = paid.has(currentMonth)
  if (dues.monthsBehind > 0) {
    const cells = stripCells({ currentMonth, joinDate: dues.joinDate, paidMonths: paid, monthsMissed: dues.monthsBehind, countsForDues: true })
    return { title: `You are ${monthsBehind(dues.monthsBehind)}`, detail: [lastPayment, owedSummary(cells).sentence].filter(Boolean).join(' ') }
  }
  const through = currentPaid ? currentMonth : shiftMonth(currentMonth, -1)
  const next = currentPaid ? `Next dues are for ${longMonth(shiftMonth(currentMonth, 1))}.` : `Dues for ${longMonth(currentMonth)} are due now.`
  return { title: `You are paid up through ${longMonth(through)}`, detail: [lastPayment, next].filter(Boolean).join(' ') }
}
