import { describe, it, expect } from 'vitest'
import { meterSegments, monthsBehind } from '@/utils/dashboardMeter'

describe('meterSegments', () => {
  it('uses one segment per member for small groups', () => {
    expect(meterSegments(14, 9)).toEqual({ segments: 14, filled: 9 })
  })

  it('caps the segments and scales the filled count', () => {
    expect(meterSegments(80, 40)).toEqual({ segments: 40, filled: 20 })
    expect(meterSegments(100, 100)).toEqual({ segments: 40, filled: 40 })
  })

  it('returns no segments when there are no members', () => {
    expect(meterSegments(0, 0)).toEqual({ segments: 0, filled: 0 })
    expect(meterSegments(0, 5)).toEqual({ segments: 0, filled: 0 })
  })

  it('clamps paid to the range 0..total', () => {
    expect(meterSegments(10, -3)).toEqual({ segments: 10, filled: 0 })
    expect(meterSegments(10, 25)).toEqual({ segments: 10, filled: 10 })
  })

  it('honours a custom maximum', () => {
    expect(meterSegments(30, 15, 10)).toEqual({ segments: 10, filled: 5 })
  })
})

describe('monthsBehind', () => {
  it('uses the singular for one month', () => {
    expect(monthsBehind(1)).toBe('1 month behind')
  })

  it('uses the plural otherwise', () => {
    expect(monthsBehind(2)).toBe('2 months behind')
    expect(monthsBehind(6)).toBe('6 months behind')
  })
})
