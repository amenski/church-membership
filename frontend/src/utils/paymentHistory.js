import { PAYMENT_METHODS } from '@/utils/paymentPayload'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const cents = (amount) => Math.round((Number(amount) || 0) * 100)

/**
 * The three figures above the history. currentPeriod is "YYYY-MM"; "this month" is the month a
 * payment covers (its period), not the day it was entered, so a back-dated payment does not count.
 * @returns {{ thisMonth: number, allTime: number, average: number }}
 */
export function paymentsSummary(payments, currentPeriod) {
  const list = Array.isArray(payments) ? payments : []
  let thisMonth = 0
  let allTime = 0
  for (const payment of list) {
    allTime += cents(payment.amount)
    if (payment.period === currentPeriod) thisMonth += cents(payment.amount)
  }
  return {
    thisMonth: thisMonth / 100,
    allTime: allTime / 100,
    average: list.length ? Math.round(allTime / list.length) / 100 : 0
  }
}

/** "2026-10" becomes "Oct 2026"; anything that is not a YYYY-MM string becomes "". */
export function periodLabel(period) {
  const match = typeof period === 'string' ? /^(\d{4})-(0[1-9]|1[0-2])$/.exec(period) : null
  return match ? `${MONTHS[Number(match[2]) - 1]} ${match[1]}` : ''
}

/** "R-000012": the receipt number is derived from the payment id. */
export function receiptNumber(payment) {
  return `R-${String(payment.id).padStart(6, '0')}`
}

/** The method's label ("Bank transfer"); an unknown code is shown as it came. */
export function methodLabel(value) {
  return PAYMENT_METHODS.find(method => method.value === value)?.label || value || ''
}

/** Newest first: paid on descending, then id descending. Does not change the input. */
export function sortPayments(payments) {
  return [...payments].sort((a, b) => {
    const byDate = String(b.paymentDate || '').localeCompare(String(a.paymentDate || ''))
    return byDate || b.id - a.id
  })
}

/** Search by member name (case-insensitive) and an optional method code ('' or 'ALL' is any). */
export function filterPayments(payments, { search = '', method = 'ALL' } = {}) {
  const term = search.trim().toLowerCase()
  return payments.filter(payment => {
    if (method && method !== 'ALL' && payment.paymentMethod !== method) return false
    return !term || (payment.member?.name || '').toLowerCase().includes(term)
  })
}
