export const PAYMENT_METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CREDIT_CARD', label: 'Credit card' },
  { value: 'DEBIT_CARD', label: 'Debit card' },
  { value: 'MOBILE_PAYMENT', label: 'Mobile payment' },
  { value: 'ONLINE_PAYMENT', label: 'Online payment' },
  { value: 'CHECK', label: 'Check' }
]

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
