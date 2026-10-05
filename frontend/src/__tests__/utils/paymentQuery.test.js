import { describe, expect, it } from 'vitest'
import { ariaSort, filtersQuery, formatSort, nextSort, pageParams, parseSort, readFilters, sortLabel } from '@/utils/paymentQuery'
import { paidMonthsFromMap } from '@/utils/yearStrip'

const DEFAULT = { field: 'paymentDate', direction: 'desc' }

describe('parseSort and formatSort', () => {
  it('reads field,direction and writes it back', () => {
    expect(parseSort('amount,asc')).toEqual({ field: 'amount', direction: 'asc' })
    expect(formatSort({ field: 'member', direction: 'desc' })).toBe('member,desc')
  })

  it('falls back to the default for a missing, unknown or malformed value', () => {
    for (const value of [undefined, '', 'amount', 'notes,asc', 'amount,sideways', 'amount,asc,x', ['amount,asc'], 'toString,asc']) {
      expect(parseSort(value)).toEqual(DEFAULT)
    }
  })
})

describe('nextSort and ariaSort', () => {
  it('starts a new column in its natural direction: names A to Z, dates and amounts newest or highest first', () => {
    expect(nextSort(DEFAULT, 'member')).toEqual({ field: 'member', direction: 'asc' })
    expect(nextSort(DEFAULT, 'amount')).toEqual({ field: 'amount', direction: 'desc' })
    expect(nextSort({ field: 'amount', direction: 'asc' }, 'period')).toEqual({ field: 'period', direction: 'desc' })
  })

  it('flips the direction of the sorted column', () => {
    expect(nextSort(DEFAULT, 'paymentDate')).toEqual({ field: 'paymentDate', direction: 'asc' })
    expect(nextSort({ field: 'member', direction: 'asc' }, 'member')).toEqual({ field: 'member', direction: 'desc' })
  })

  it('marks only the sorted column', () => {
    expect(ariaSort(DEFAULT, 'paymentDate')).toBe('descending')
    expect(ariaSort({ field: 'member', direction: 'asc' }, 'member')).toBe('ascending')
    expect(ariaSort(DEFAULT, 'amount')).toBe('none')
  })

  it('says how the list is ordered', () => {
    expect(sortLabel(DEFAULT)).toBe('Paid on, newest first')
    expect(sortLabel({ field: 'member', direction: 'desc' })).toBe('Member, Z to A')
    expect(sortLabel({ field: 'amount', direction: 'desc' })).toBe('Amount, highest first')
  })
})

describe('readFilters and filtersQuery', () => {
  it('reads search, method and sort from the query', () => {
    expect(readFilters({ search: ' Abebe ', method: 'cash', sort: 'amount,asc' })).toEqual({
      search: 'Abebe',
      method: 'CASH',
      sort: { field: 'amount', direction: 'asc' }
    })
  })

  it('an empty, missing or unknown value is the default', () => {
    const none = { search: '', method: '', sort: DEFAULT }
    expect(readFilters({})).toEqual(none)
    expect(readFilters(undefined)).toEqual(none)
    expect(readFilters({ method: 'BITCOIN', sort: 'nope', search: 5 })).toEqual(none)
    expect(readFilters({ search: ['a', 'b'] }).search).toBe('a')
  })

  it('leaves the defaults out of the URL', () => {
    expect(filtersQuery({ search: '  ', method: '', sort: DEFAULT })).toEqual({ search: undefined, method: undefined, sort: undefined })
    expect(filtersQuery({ search: ' Abebe ', method: 'CHECK', sort: { field: 'member', direction: 'asc' } })).toEqual({
      search: 'Abebe',
      method: 'CHECK',
      sort: 'member,asc'
    })
  })

  it('what is written is read back', () => {
    const filters = { search: 'Abebe', method: 'BANK_TRANSFER', sort: { field: 'period', direction: 'asc' } }
    expect(readFilters(filtersQuery(filters))).toEqual(filters)
  })
})

describe('pageParams', () => {
  it('turns the 1-based page into the server page and trims the search', () => {
    expect(pageParams({ page: 3, size: 50, search: ' keb ', method: 'CASH', sort: { field: 'member', direction: 'asc' } }))
      .toEqual({ page: 2, size: 50, search: 'keb', method: 'CASH', sort: 'member,asc' })
  })

  it('leaves an empty search and any method out', () => {
    expect(pageParams({ page: 1, size: 25, search: '   ', method: '', sort: DEFAULT }))
      .toEqual({ page: 0, size: 25, sort: 'paymentDate,desc' })
  })
})

describe('paidMonthsFromMap', () => {
  it('turns the paid-months object into memberId (a number) -> Set of months', () => {
    const byMember = paidMonthsFromMap({ 4: ['2026-08', '2026-10'], 7: ['2026-10'] })
    expect([...byMember.keys()]).toEqual([4, 7])
    expect(byMember.get(4)).toEqual(new Set(['2026-08', '2026-10']))
    expect(byMember.get(9)).toBeUndefined()
  })

  it('an empty or missing response is an empty map', () => {
    expect(paidMonthsFromMap({}).size).toBe(0)
    expect(paidMonthsFromMap(undefined).size).toBe(0)
    expect(paidMonthsFromMap([]).size).toBe(0)
  })
})
