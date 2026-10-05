import { describe, it, expect } from 'vitest'
import { ELLIPSIS as E, clampPage, pageItems, pageRange, pageSlice, pagingQuery, readPaging, totalPages } from '@/utils/paging'

const rows = (n) => Array.from({ length: n }, (_, i) => i + 1)

describe('totalPages', () => {
  it('rounds up and is never below 1', () => {
    expect(totalPages(134, 25)).toBe(6)
    expect(totalPages(50, 25)).toBe(2)
    expect(totalPages(1, 25)).toBe(1)
    expect(totalPages(0, 25)).toBe(1)
  })
})

describe('clampPage', () => {
  it('keeps a page inside 1..last', () => {
    expect(clampPage(3, 134, 25)).toBe(3)
    expect(clampPage(0, 134, 25)).toBe(1)
    expect(clampPage(-4, 134, 25)).toBe(1)
    expect(clampPage(99, 134, 25)).toBe(6)
  })

  it('reads anything that is not a number as page 1 and cuts a fraction', () => {
    expect(clampPage(undefined, 134, 25)).toBe(1)
    expect(clampPage('abc', 134, 25)).toBe(1)
    expect(clampPage(NaN, 134, 25)).toBe(1)
    expect(clampPage('2', 134, 25)).toBe(2)
    expect(clampPage(2.9, 134, 25)).toBe(2)
  })

  it('is page 1 for an empty list', () => {
    expect(clampPage(5, 0, 25)).toBe(1)
  })
})

describe('pageSlice', () => {
  it('returns the rows of the page', () => {
    expect(pageSlice(rows(134), 1, 25)).toEqual(rows(25))
    expect(pageSlice(rows(134), 2, 25)).toEqual(rows(50).slice(25))
  })

  it('returns the short last page', () => {
    expect(pageSlice(rows(134), 6, 25)).toEqual([126, 127, 128, 129, 130, 131, 132, 133, 134])
  })

  it('shows the last page for a page beyond it and the first for one below', () => {
    expect(pageSlice(rows(30), 9, 25)).toEqual([26, 27, 28, 29, 30])
    expect(pageSlice(rows(30), 0, 25)).toEqual(rows(25))
  })

  it('returns everything when it fits, and nothing for an empty list', () => {
    expect(pageSlice(rows(7), 1, 10)).toEqual(rows(7))
    expect(pageSlice([], 1, 10)).toEqual([])
  })

  it('does not change the list it is given', () => {
    const list = rows(30)
    pageSlice(list, 2, 10)
    expect(list).toEqual(rows(30))
  })
})

describe('pageRange', () => {
  it('gives the first and last row of the page', () => {
    expect(pageRange(2, 25, 134)).toEqual({ from: 26, to: 50 })
    expect(pageRange(6, 25, 134)).toEqual({ from: 126, to: 134 })
    expect(pageRange(1, 25, 7)).toEqual({ from: 1, to: 7 })
  })

  it('is 0 to 0 for an empty list and clamps a page beyond the last', () => {
    expect(pageRange(1, 25, 0)).toEqual({ from: 0, to: 0 })
    expect(pageRange(9, 25, 30)).toEqual({ from: 26, to: 30 })
  })
})

describe('pageItems', () => {
  it('lists every page up to 7', () => {
    expect(pageItems(1, 1)).toEqual([1])
    expect(pageItems(3, 5)).toEqual([1, 2, 3, 4, 5])
    expect(pageItems(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('opens with the first pages and an ellipsis near the start', () => {
    for (const page of [1, 2, 3, 4]) expect(pageItems(page, 12)).toEqual([1, 2, 3, 4, 5, E, 12])
  })

  it('closes with the last pages and an ellipsis near the end', () => {
    for (const page of [9, 10, 11, 12]) expect(pageItems(page, 12)).toEqual([1, E, 8, 9, 10, 11, 12])
  })

  it('shows the neighbours between two ellipses in the middle', () => {
    expect(pageItems(5, 12)).toEqual([1, E, 4, 5, 6, E, 12])
    expect(pageItems(6, 12)).toEqual([1, E, 5, 6, 7, E, 12])
    expect(pageItems(8, 12)).toEqual([1, E, 7, 8, 9, E, 12])
  })

  it('switches from 7 pages to the window at 8 pages', () => {
    expect(pageItems(1, 8)).toEqual([1, 2, 3, 4, 5, E, 8])
    expect(pageItems(4, 8)).toEqual([1, 2, 3, 4, 5, E, 8])
    expect(pageItems(5, 8)).toEqual([1, E, 4, 5, 6, 7, 8])
  })

  it('is never longer than 7, always holds the current page and the ends', () => {
    for (const pages of [8, 9, 20, 134]) {
      for (let page = 1; page <= pages; page++) {
        const items = pageItems(page, pages)
        expect(items.length).toBeLessThanOrEqual(7)
        expect(items).toContain(page)
        expect(items[0]).toBe(1)
        expect(items[items.length - 1]).toBe(pages)
      }
    }
  })
})

describe('readPaging', () => {
  it('reads page and size from a query', () => {
    expect(readPaging({ page: '2', size: '50' })).toEqual({ page: 2, size: 50 })
  })

  it('falls back to page 1 and the default size', () => {
    expect(readPaging(undefined)).toEqual({ page: 1, size: 25 })
    expect(readPaging({ page: 'x', size: '7' })).toEqual({ page: 1, size: 25 })
    expect(readPaging({ page: '0' })).toEqual({ page: 1, size: 25 })
    expect(readPaging({ page: '-3' })).toEqual({ page: 1, size: 25 })
    expect(readPaging({ page: '1.5' })).toEqual({ page: 1, size: 25 })
  })

  it('takes the default size and the sizes a screen allows', () => {
    expect(readPaging({}, 10, [10, 25, 50])).toEqual({ page: 1, size: 10 })
    expect(readPaging({ size: '100' }, 10, [10, 25, 50])).toEqual({ page: 1, size: 10 })
    expect(readPaging({ size: '50' }, 10, [10, 25, 50])).toEqual({ page: 1, size: 50 })
  })
})

describe('pagingQuery', () => {
  it('leaves page and size out when they are the defaults', () => {
    expect(pagingQuery({ page: '3', size: '50' }, 1, 25)).toEqual({})
    expect(pagingQuery({}, 1, 10, 10)).toEqual({})
  })

  it('writes them as strings when they are not', () => {
    expect(pagingQuery({}, 2, 50)).toEqual({ page: '2', size: '50' })
    expect(pagingQuery({}, 3, 25)).toEqual({ page: '3' })
    expect(pagingQuery({}, 1, 10)).toEqual({ size: '10' })
  })

  it('keeps the other keys and does not change the query it is given', () => {
    const query = { id: '4', page: '9' }
    expect(pagingQuery(query, 2, 25)).toEqual({ id: '4', page: '2' })
    expect(query).toEqual({ id: '4', page: '9' })
    expect(pagingQuery(undefined, 2, 25)).toEqual({ page: '2' })
  })
})
