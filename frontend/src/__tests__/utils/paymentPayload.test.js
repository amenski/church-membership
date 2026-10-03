import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'

// Shared with the backend PaymentContractTest.
// vitest runs from frontend/ (jsdom makes import.meta.url a non-file URL)
const fixturePath = resolve(process.cwd(), '../src/test/resources/contracts/record-payment-request.json')
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))

describe('buildPaymentRequest', () => {
  it('matches the shared contract fixture', () => {
    const form = { memberId: '1', amount: '50', paymentMethod: 'CASH', period: '2026-10' }
    expect(buildPaymentRequest(form)).toEqual(fixture)
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
