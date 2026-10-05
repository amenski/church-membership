import { describe, it, expect } from 'vitest'
import { nextMonthCovered } from '@/utils/nextMonthCovered'

const NOW = '2026-10'

describe('nextMonthCovered', () => {
  it('sets the oldest unpaid month while the field still holds the last automatic value', () => {
    expect(nextMonthCovered(NOW, NOW, '2026-07', NOW)).toEqual({ period: '2026-07', auto: '2026-07' })
  })

  it('goes back to the current month when the next member has nothing unpaid', () => {
    expect(nextMonthCovered('2026-07', '2026-07', '', NOW)).toEqual({ period: NOW, auto: NOW })
  })

  it('goes back to the current month when the member is cleared', () => {
    expect(nextMonthCovered('2026-07', '2026-07', '', NOW).period).toBe(NOW)
  })

  it('keeps a hand-typed month for any next member', () => {
    expect(nextMonthCovered('2026-07', '2026-05', '', NOW)).toEqual({ period: '2026-05', auto: '2026-07' })
    expect(nextMonthCovered('2026-07', '2026-05', '2026-08', NOW)).toEqual({ period: '2026-05', auto: '2026-07' })
  })
})
