import { describe, it, expect } from 'vitest'
import { audienceCount } from '@/utils/audienceCount'

const members = [
  { id: 1, active: true, consecutiveMonthsMissed: 0 },
  { id: 2, active: true, consecutiveMonthsMissed: 1 },
  { id: 3, active: true, consecutiveMonthsMissed: 3 },
  { id: 4, active: false, consecutiveMonthsMissed: 5 }
]

describe('audienceCount', () => {
  it('counts active members for ALL', () => {
    expect(audienceCount(members, 'ALL', 1)).toBe(3)
  })

  it('counts only ACTIVE members who are behind for OVERDUE (an inactive one never counts)', () => {
    expect(audienceCount(members, 'OVERDUE', 1)).toBe(2)
    expect(audienceCount(members, 'OVERDUE', 3)).toBe(1)
    expect(audienceCount(members, 'OVERDUE', '2')).toBe(1)
    expect(audienceCount(members, 'OVERDUE', 4)).toBe(0)
  })

  it('is 0 for OVERDUE with a missing or invalid number of months', () => {
    expect(audienceCount(members, 'OVERDUE', '')).toBe(0)
    expect(audienceCount(members, 'OVERDUE', 0)).toBe(0)
    expect(audienceCount(members, 'OVERDUE', 'abc')).toBe(0)
  })

  it('is 1 for SPECIFIC and 0 for an unknown audience', () => {
    expect(audienceCount(members, 'SPECIFIC', 1)).toBe(1)
    expect(audienceCount(members, 'NOBODY', 1)).toBe(0)
  })

  it('handles an empty or missing list', () => {
    expect(audienceCount([], 'ALL', 1)).toBe(0)
    expect(audienceCount(undefined, 'OVERDUE', 1)).toBe(0)
  })
})
