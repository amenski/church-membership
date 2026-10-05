import { formatDate } from '@/utils'
// methodKey lives next to the method list; re-exported so payment code has one import for its words
export { methodKey } from '@/utils/paymentPayload'

/** "2026-10" becomes "Oct 2026" in the UI language; anything that is not a YYYY-MM string becomes "". */
export function periodLabel(period) {
  const match = typeof period === 'string' ? /^(\d{4})-(0[1-9]|1[0-2])$/.exec(period) : null
  return match ? formatDate(`${period}-01`, 'MMM yyyy') : ''
}

/** "R-000012": the receipt number is derived from the payment id. */
export function receiptNumber(payment) {
  return `R-${String(payment.id).padStart(6, '0')}`
}

/** Newest first: paid on descending, then id descending. Does not change the input. */
export function sortPayments(payments) {
  return [...payments].sort((a, b) => {
    const byDate = String(b.paymentDate || '').localeCompare(String(a.paymentDate || ''))
    return byDate || b.id - a.id
  })
}
