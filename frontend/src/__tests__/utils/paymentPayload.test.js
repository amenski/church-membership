import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'

// Shared with the backend PaymentContractTest.
// vitest runs from frontend/ (jsdom makes import.meta.url a non-file URL)
const readFixture = (name) =>
  JSON.parse(readFileSync(resolve(process.cwd(), '../src/test/resources/contracts', name), 'utf8'))
const fixture = readFixture('record-payment-request.json')
const backfillFixture = readFixture('record-payment-request-backfill.json')

describe('buildPaymentRequest', () => {
  it('matches the shared contract fixture', () => {
    const form = { memberId: '1', amount: '50', paymentMethod: 'CASH', period: '2026-10' }
    expect(buildPaymentRequest(form)).toEqual(fixture)
  })

  it('matches the back-dated fixture (period and paymentDate)', () => {
    const form = { memberId: '1', amount: '50', paymentMethod: 'CHECK', period: '2024-03', paymentDate: '2024-03-10' }
    expect(buildPaymentRequest(form)).toEqual(backfillFixture)
  })

  it('omits paymentDate when blank so the server uses today', () => {
    const form = { memberId: '1', amount: '50', paymentMethod: 'CASH', period: '2026-10', paymentDate: '' }
    expect(buildPaymentRequest(form)).not.toHaveProperty('paymentDate')
  })

  it('omits notes when empty and keeps them otherwise', () => {
    const form = { memberId: 1, amount: 5, paymentMethod: 'CASH', period: '2026-10' }
    expect(buildPaymentRequest({ ...form, notes: '' })).not.toHaveProperty('notes')
    expect(buildPaymentRequest({ ...form, notes: 'paid in person' }).notes).toBe('paid in person')
  })

  it('coerces memberId and amount to numbers', () => {
    const r = buildPaymentRequest({ memberId: '7', amount: '12.5', paymentMethod: 'CASH', period: '2026-10' })
    expect(r.memberId).toBe(7)
    expect(r.amount).toBe(12.5)
  })
})

describe('PAYMENT_METHODS', () => {
  it('lists the seven backend codes', () => {
    expect(PAYMENT_METHODS.map(m => m.value)).toEqual([
      'CASH', 'BANK_TRANSFER', 'CREDIT_CARD', 'DEBIT_CARD', 'MOBILE_PAYMENT', 'ONLINE_PAYMENT', 'CHECK'
    ])
  })
})
