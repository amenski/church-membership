// The year strip: twelve squares, the last twelve months ending with the current month, oldest first.
//
// The rule that keeps the strip and the "N months behind" text from disagreeing. The server owns the
// count: `consecutiveMonthsMissed` goes up by one on the 1st of a month for each dues-paying member
// (status MEMBER, joined by the end of last month) with no payment for last month, and goes back to 0
// when a payment for the CURRENT month is recorded or the member is reactivated
// (UpdateMissingPaymentCountersUseCase, Member.recordPayment). So a square is:
//
//   paid      the member has a payment whose period is that month
//   due       the current month, unpaid: not late yet, the server has not counted it
//   missed    an earlier month on or after the join month, unpaid, and one of the member's most
//             recent `monthsMissed` unpaid months. The red squares in the window therefore never
//             outnumber the server's figure, and equal it when the whole run is inside the window
//   uncounted an earlier unpaid month the server no longer counts (the count was reset by a
//             payment this month or by reactivation): drawn dashed like "not a member"
//   none      before the join month, or any unpaid month of a member who does not owe dues
//             (inactive, deceased, transferred, archived): the server stopped counting them
//
// Months are "yyyy-MM" strings, which compare correctly as text.

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export const STRIP_LABELS = {
  paid: 'Paid',
  missed: 'Missed',
  due: 'Due now, unpaid',
  none: 'Not a member that month',
  uncounted: 'Unpaid, no longer counted'
}

// Tokens only: teal paid, clay hatching with a clay edge missed, ochre outline due now, dashed field edge for the rest
export const SQUARES = {
  paid: 'bg-teal',
  missed: 'border border-clay bg-[repeating-linear-gradient(135deg,var(--color-clay-tint)_0_2px,var(--color-clay)_2px_4px)]',
  due: 'border-2 border-ochre-edge bg-paper',
  none: 'border border-dashed border-field',
  uncounted: 'border border-dashed border-field'
}

const pad = (n) => String(n).padStart(2, '0')

/** The twelve "yyyy-MM" keys ending with currentMonth, oldest first. */
export function stripMonths(currentMonth) {
  const [year, month] = currentMonth.split('-').map(Number)
  const months = []
  for (let back = 11; back >= 0; back--) {
    const index = year * 12 + (month - 1) - back
    months.push(`${Math.floor(index / 12)}-${pad((index % 12) + 1)}`)
  }
  return months
}

/** The twelve short month names ("Nov", "Dec", ...) ending with currentMonth, oldest first. */
export function stripMonthLabels(currentMonth) {
  return stripMonths(currentMonth).map((key) => MONTH_NAMES[Number(key.slice(5)) - 1])
}

/** "Nov to Oct" for the column header. */
export function stripRangeLabel(currentMonth) {
  const months = stripMonths(currentMonth)
  const name = (key) => MONTH_NAMES[Number(key.slice(5)) - 1]
  return `${name(months[0])} to ${name(months[11])}`
}

/**
 * @param {object} args
 * @param {string} args.currentMonth "yyyy-MM"
 * @param {string} [args.joinDate] "yyyy-MM-dd"; missing means the member has always been there
 * @param {Set<string>} args.paidMonths the periods of the member's payments
 * @param {number} args.monthsMissed the server's consecutiveMonthsMissed
 * @param {boolean} args.countsForDues status is MEMBER
 * @returns {{ month: string, state: string, label: string, initial: string, name: string }[]}
 */
export function stripCells({ currentMonth, joinDate, paidMonths, monthsMissed, countsForDues }) {
  const joinMonth = joinDate ? joinDate.slice(0, 7) : ''
  const months = stripMonths(currentMonth)
  const states = months.map((month) => {
    if (paidMonths.has(month)) return 'paid'
    if (!countsForDues || month < joinMonth) return 'none'
    return month === currentMonth ? 'due' : 'uncounted'
  })
  // the server's count covers the most recent unpaid months: turn that many, newest first, into missed
  let remaining = monthsMissed
  for (let i = states.length - 1; i >= 0 && remaining > 0; i--) {
    if (states[i] === 'uncounted') {
      states[i] = 'missed'
      remaining--
    }
  }
  return months.map((month, i) => {
    const monthName = MONTH_NAMES[Number(month.slice(5)) - 1]
    return {
      month,
      state: states[i],
      label: STRIP_LABELS[states[i]],
      initial: monthName[0],
      name: `${monthName} ${month.slice(0, 4)}`
    }
  })
}

/** memberId -> Set of "yyyy-MM" periods, from the payments list. */
export function paidMonthsByMember(payments) {
  const byMember = new Map()
  for (const payment of Array.isArray(payments) ? payments : []) {
    const id = payment?.member?.id
    if (id == null || !payment.period) continue
    if (!byMember.has(id)) byMember.set(id, new Set())
    byMember.get(id).add(String(payment.period))
  }
  return byMember
}
