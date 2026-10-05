import { describe, it, expect } from 'vitest'
import { methodLabel, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'

describe('periodLabel', () => {
  it('formats YYYY-MM', () => {
    expect(periodLabel('2026-10')).toBe('Oct 2026')
    expect(periodLabel('2024-01')).toBe('Jan 2024')
    expect(periodLabel('2023-12')).toBe('Dec 2023')
  })
  it('is empty for anything else', () => {
    expect(periodLabel('')).toBe('')
    expect(periodLabel(null)).toBe('')
    expect(periodLabel('2026-13')).toBe('')
    expect(periodLabel('2026-10-01')).toBe('')
  })
})

describe('receiptNumber and methodLabel', () => {
  it('pads the id to six digits', () => {
    expect(receiptNumber({ id: 12 })).toBe('R-000012')
  })
  it('maps a code to its label and passes an unknown one through', () => {
    expect(methodLabel('BANK_TRANSFER')).toBe('Bank transfer')
    expect(methodLabel('WIRE')).toBe('WIRE')
    expect(methodLabel(null)).toBe('')
  })
})

describe('sortPayments', () => {
  it('puts the latest paid-on first, then the higher id', () => {
    const list = [
      { id: 1, paymentDate: '2026-01-05' },
      { id: 3, paymentDate: '2026-02-01' },
      { id: 2, paymentDate: '2026-02-01' }
    ]
    expect(sortPayments(list).map(p => p.id)).toEqual([3, 2, 1])
    expect(list.map(p => p.id)).toEqual([1, 3, 2])
  })
})
