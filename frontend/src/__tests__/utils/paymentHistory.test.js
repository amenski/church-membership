import { describe, it, expect } from 'vitest'
import { filterPayments, methodLabel, paymentsSummary, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'

describe('paymentsSummary', () => {
  it('is all zeros for an empty list', () => {
    expect(paymentsSummary([], '2026-10')).toEqual({ thisMonth: 0, allTime: 0, average: 0 })
    expect(paymentsSummary(undefined, '2026-10')).toEqual({ thisMonth: 0, allTime: 0, average: 0 })
  })

  it('sums the current period, everything, and the average', () => {
    const payments = [
      { amount: 50, period: '2026-10' },
      { amount: 25.5, period: '2026-10' },
      { amount: 100, period: '2026-09' }
    ]
    expect(paymentsSummary(payments, '2026-10')).toEqual({ thisMonth: 75.5, allTime: 175.5, average: 58.5 })
  })

  it('counts by the month covered, not the day it was paid', () => {
    const payments = [{ amount: 40, period: '2026-03', paymentDate: '2026-10-01' }]
    expect(paymentsSummary(payments, '2026-10').thisMonth).toBe(0)
  })

  it('does not drift on cents', () => {
    const payments = [{ amount: 0.1, period: 'a' }, { amount: 0.2, period: 'a' }]
    expect(paymentsSummary(payments, 'a').allTime).toBe(0.3)
  })
})

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

describe('filterPayments', () => {
  const list = [
    { id: 1, member: { name: 'John Doe' }, paymentMethod: 'CASH' },
    { id: 2, member: { name: 'Jane Roe' }, paymentMethod: 'CHECK' },
    { id: 3, paymentMethod: 'CASH' }
  ]
  it('returns everything with no filters', () => {
    expect(filterPayments(list, {})).toHaveLength(3)
    expect(filterPayments(list, { search: ' ', method: 'ALL' })).toHaveLength(3)
  })
  it('searches the member name, ignoring case', () => {
    expect(filterPayments(list, { search: 'JANE' }).map(p => p.id)).toEqual([2])
  })
  it('filters by method and combines with search', () => {
    expect(filterPayments(list, { method: 'CASH' }).map(p => p.id)).toEqual([1, 3])
    expect(filterPayments(list, { method: 'CASH', search: 'doe' }).map(p => p.id)).toEqual([1])
  })
})
