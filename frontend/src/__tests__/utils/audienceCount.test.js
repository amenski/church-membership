import { describe, it, expect } from 'vitest'
import { audienceCount } from '@/utils/audienceCount'

const members = [
  { id: 1, email: 'a@example.com', status: 'MEMBER', consecutiveMonthsMissed: 0 },
  { id: 2, email: 'b@example.com', status: 'MEMBER', consecutiveMonthsMissed: 1 },
  { id: 3, email: 'c@example.com', status: 'MEMBER', consecutiveMonthsMissed: 3 },
  { id: 4, email: 'd@example.com', status: 'INACTIVE', consecutiveMonthsMissed: 5 }
]

describe('audienceCount', () => {
  it('counts MEMBER-status members for ALL', () => {
    expect(audienceCount(members, 'ALL', 1)).toBe(3)
  })

  it('counts only MEMBER-status members who are behind for OVERDUE (any other status never counts)', () => {
    expect(audienceCount(members, 'OVERDUE', 1)).toBe(2)
    expect(audienceCount(members, 'OVERDUE', 3)).toBe(1)
    expect(audienceCount(members, 'OVERDUE', '2')).toBe(1)
    expect(audienceCount(members, 'OVERDUE', 4)).toBe(0)
  })

  it('never counts inactive, deceased, transferred or archived members, even with an email and months behind', () => {
    const others = ['INACTIVE', 'DECEASED', 'TRANSFERRED', 'ARCHIVED'].map((status, i) => (
      { id: 10 + i, email: `x${i}@example.com`, status, consecutiveMonthsMissed: 4 }
    ))
    expect(audienceCount(others, 'ALL', 1)).toBe(0)
    expect(audienceCount(others, 'OVERDUE', 1)).toBe(0)
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

  it('skips members without an email, empty or missing', () => {
    const list = [
      ...members,
      { id: 5, email: null, status: 'MEMBER', consecutiveMonthsMissed: 2 },
      { id: 6, email: '  ', status: 'MEMBER', consecutiveMonthsMissed: 2 },
      { id: 7, status: 'MEMBER', consecutiveMonthsMissed: 2 }
    ]
    expect(audienceCount(list, 'ALL', 1)).toBe(3)
    expect(audienceCount(list, 'OVERDUE', 2)).toBe(1)
  })

  it('counts one message for members who share an address, ignoring case and spaces', () => {
    const family = [
      { id: 1, email: 'family@example.com', status: 'MEMBER', consecutiveMonthsMissed: 2 },
      { id: 2, email: ' FAMILY@Example.com ', status: 'MEMBER', consecutiveMonthsMissed: 2 },
      { id: 3, email: 'other@example.com', status: 'MEMBER', consecutiveMonthsMissed: 0 }
    ]
    expect(audienceCount(family, 'ALL', 1)).toBe(2)
    expect(audienceCount(family, 'OVERDUE', 1)).toBe(1)
  })

  it('handles an empty or missing list', () => {
    expect(audienceCount([], 'ALL', 1)).toBe(0)
    expect(audienceCount(undefined, 'OVERDUE', 1)).toBe(0)
  })
})
