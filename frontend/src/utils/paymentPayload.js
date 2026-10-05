// The seven backend payment codes; the words live in locales/ (methods.*)
export const PAYMENT_METHODS = [
  { value: 'CASH', labelKey: 'methods.cash' },
  { value: 'BANK_TRANSFER', labelKey: 'methods.bankTransfer' },
  { value: 'CREDIT_CARD', labelKey: 'methods.creditCard' },
  { value: 'DEBIT_CARD', labelKey: 'methods.debitCard' },
  { value: 'MOBILE_PAYMENT', labelKey: 'methods.mobilePayment' },
  { value: 'ONLINE_PAYMENT', labelKey: 'methods.onlinePayment' },
  { value: 'CHECK', labelKey: 'methods.check' }
]

/** The i18n key of a method's name; an unknown code is shown as it came. */
export function methodKey(value) {
  return PAYMENT_METHODS.find(method => method.value === value)?.labelKey || value || ''
}

// Body of POST /api/payments (RecordPaymentRequest). period is "YYYY-MM", paymentDate "YYYY-MM-DD"
// (optional: the server uses today when it is missing).
export function buildPaymentRequest(form) {
  const request = {
    memberId: Number(form.memberId),
    amount: Number(form.amount),
    paymentMethod: form.paymentMethod,
    period: form.period
  }
  if (form.paymentDate) request.paymentDate = form.paymentDate
  if (form.notes) request.notes = form.notes
  return request
}
