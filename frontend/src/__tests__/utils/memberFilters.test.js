import { describe, it, expect } from 'vitest'
import { filterMembers, sortMembers, exportIds } from '@/utils/memberFilters'

const members = [
  { id: 1, name: 'Alice Smith', email: 'alice@example.com', phone: '(06) 12-34', active: true, joinDate: '2023-01-10', consecutiveMonthsMissed: 0 },
  { id: 2, name: 'bob Jones', email: 'bob@test.org', phone: '555-9999', active: false, joinDate: '2024-05-20', consecutiveMonthsMissed: 3 },
  { id: 3, name: 'Carol White', email: null, phone: null, active: true, joinDate: null, consecutiveMonthsMissed: 1 }
]
const none = { search: '', status: 'ALL', paymentStatus: 'ALL', joinedFrom: '', joinedTo: '' }
const ids = (list) => list.map(m => m.id)
const run = (f) => ids(filterMembers(members, { ...none, ...f }))

describe('filterMembers', () => {
  it('returns everything with no filters', () => expect(run({})).toEqual([1, 2, 3]))
  it('searches name case-insensitively and trimmed', () => expect(run({ search: '  BOB ' })).toEqual([2]))
  it('searches email', () => expect(run({ search: 'example.com' })).toEqual([1]))
  it('searches phone literally', () => expect(run({ search: '555-9' })).toEqual([2]))
  it('matches phone by digits only when 3+ digits', () => {
    expect(run({ search: '0612' })).toEqual([1])
    expect(run({ search: '06 12 34' })).toEqual([1])
  })
  it('does not digit-match with fewer than 3 digits', () => expect(run({ search: '61' })).toEqual([]))
  it('filters by status', () => {
    expect(run({ status: 'ACTIVE' })).toEqual([1, 3])
    expect(run({ status: 'INACTIVE' })).toEqual([2])
  })
  it('filters by dues for active members only', () => {
    expect(run({ paymentStatus: 'OVERDUE' })).toEqual([3])
    expect(run({ paymentStatus: 'CURRENT' })).toEqual([1])
  })
  it('never matches an inactive member with missed months to Behind', () => {
    expect(run({ paymentStatus: 'OVERDUE' })).not.toContain(2)
  })
  it('never matches an inactive member to Paid up, even with zero months missed', () => {
    const list = [{ id: 9, name: 'Dan', active: false, consecutiveMonthsMissed: 0 }]
    expect(ids(filterMembers(list, { ...none, paymentStatus: 'CURRENT' }))).toEqual([])
    expect(ids(filterMembers(list, none))).toEqual([9])
  })
  it('join date bounds are inclusive', () => {
    expect(run({ joinedFrom: '2023-01-10' })).toEqual([1, 2])
    expect(run({ joinedTo: '2023-01-10' })).toEqual([1])
    expect(run({ joinedFrom: '2023-01-10', joinedTo: '2024-05-20' })).toEqual([1, 2])
  })
  it('excludes members without joinDate only when a bound is set', () => {
    expect(run({})).toContain(3)
    expect(run({ joinedTo: '2030-01-01' })).not.toContain(3)
  })
  it('is null-safe for missing email and phone', () => expect(run({ search: 'carol' })).toEqual([3]))
  it('combines filters', () => {
    expect(run({ status: 'ACTIVE', paymentStatus: 'OVERDUE', search: 'carol' })).toEqual([3])
    expect(run({ status: 'ACTIVE', joinedFrom: '2024-01-01' })).toEqual([])
  })
})

describe('sortMembers', () => {
  it('sorts names case-insensitively', () => {
    expect(ids(sortMembers(members, 'name', 'asc'))).toEqual([1, 2, 3])
    expect(ids(sortMembers(members, 'name', 'desc'))).toEqual([3, 2, 1])
  })
  it('sorts join date with nulls last in both directions', () => {
    expect(ids(sortMembers(members, 'joinDate', 'asc'))).toEqual([1, 2, 3])
    expect(ids(sortMembers(members, 'joinDate', 'desc'))).toEqual([2, 1, 3])
  })
  it('sorts months missed', () => {
    expect(ids(sortMembers(members, 'consecutiveMonthsMissed', 'asc'))).toEqual([1, 3, 2])
    expect(ids(sortMembers(members, 'consecutiveMonthsMissed', 'desc'))).toEqual([3, 1, 2])
  })
  it('puts inactive members last by dues in both directions', () => {
    // member 2 is inactive with 3 months missed: that number is stale and must not rank
    expect(ids(sortMembers(members, 'consecutiveMonthsMissed', 'asc')).at(-1)).toBe(2)
    expect(ids(sortMembers(members, 'consecutiveMonthsMissed', 'desc')).at(-1)).toBe(2)
  })
  it('puts null months missed last', () => {
    const list = [{ id: 1, consecutiveMonthsMissed: null }, { id: 2, consecutiveMonthsMissed: 2 }]
    expect(ids(sortMembers(list, 'consecutiveMonthsMissed', 'desc'))).toEqual([2, 1])
  })
  it('returns a new array and does not mutate', () => {
    const copy = [...members]
    const sorted = sortMembers(members, 'name', 'desc')
    expect(sorted).not.toBe(members)
    expect(members).toEqual(copy)
  })
})

describe('exportIds', () => {
  it('returns no ids when nothing is filtered out', () => expect(exportIds(members, members)).toEqual([]))
  it('returns the visible ids when the list is filtered', () => {
    expect(exportIds([members[0], members[2]], members)).toEqual([1, 3])
  })
})
