// Wording for the member's own screen (MyDuesView). The rule is the one the year strip and the member list use:
// the server's monthsBehind counts the missed months, and a current month that is unpaid is "due now", not late.
//
// Each function takes the `t` of useI18n(): the words live in locales/, the rules stay here.
import { longMonth, monthsBehind, owedSummary } from '@/utils/dues'
import { stripCells } from '@/utils/yearStrip'
import { statusKey } from '@/utils/memberStatus'
import { methodKey } from '@/utils/paymentPayload'
import { formatDate, formatMoney } from '@/utils'

const shiftMonth = (key, by) => {
  const index = Number(key.slice(0, 4)) * 12 + (Number(key.slice(5)) - 1) + by
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

/**
 * The status card: a headline and a sentence under it.
 * @param {object} dues the GET /api/me/dues body
 * @param {string} currentMonth "yyyy-MM"
 * @param {Function} t useI18n's t
 * @returns {{ title: string, detail: string }}
 */
export function myDuesStatus(dues, currentMonth, t) {
  const paid = new Set(dues.paidMonths || [])
  if (dues.status !== 'MEMBER') {
    return {
      title: t('myDues.inactiveTitle', { status: t(statusKey(dues.status)) }),
      detail: t('myDues.inactiveDetail')
    }
  }
  const last = dues.payments?.[0]
  const lastPayment = last
    ? t('myDues.lastPayment', {
        amount: formatMoney(last.amount),
        method: t(methodKey(last.method)),
        date: formatDate(last.paymentDate, 'MMMM d')
      })
    : ''
  const currentPaid = paid.has(currentMonth)
  if (dues.monthsBehind > 0) {
    const cells = stripCells({ currentMonth, joinDate: dues.joinDate, paidMonths: paid, monthsMissed: dues.monthsBehind, countsForDues: true })
    return {
      title: t('myDues.behindTitle', { behind: monthsBehind(dues.monthsBehind, t) }),
      detail: [lastPayment, owedSummary(cells, t).sentence].filter(Boolean).join(' ')
    }
  }
  const through = currentPaid ? currentMonth : shiftMonth(currentMonth, -1)
  const next = currentPaid
    ? t('myDues.nextDues', { month: longMonth(shiftMonth(currentMonth, 1)) })
    : t('myDues.dueNow', { month: longMonth(currentMonth) })
  return {
    title: t('myDues.paidThroughTitle', { month: longMonth(through) }),
    detail: [lastPayment, next].filter(Boolean).join(' ')
  }
}

/** "Member since January 2023", or '' when the join date is missing. */
export function memberSince(joinDate, t) {
  return joinDate ? t('myDues.memberSince', { date: formatDate(joinDate, 'MMMM yyyy') }) : ''
}
