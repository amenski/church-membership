// Paging a list that is already in the browser: the slice for a page, the numbered buttons of the pager,
// and the page/size pair kept in the URL query (?page=2&size=50, left out when it is the default).

export const PAGE_SIZES = [10, 25, 50, 100]
export const DEFAULT_PAGE_SIZE = 25

/** How many pages `total` rows make at `size` rows a page: always at least 1, so an empty list is page 1 of 1. */
export function totalPages(total, size) {
  return Math.max(1, Math.ceil(total / size))
}

/** A page number held to 1..last page; anything that is not a whole number is page 1. */
export function clampPage(page, total, size) {
  const number = Math.trunc(Number(page))
  if (!Number.isFinite(number)) return 1
  return Math.min(Math.max(number, 1), totalPages(total, size))
}

/** The rows of one page (the page is clamped, so a page beyond the last shows the last). */
export function pageSlice(items, page, size) {
  const current = clampPage(page, items.length, size)
  return items.slice((current - 1) * size, current * size)
}

/** "Showing 26 to 50 of 134": the first and last row number on the page (both 0 when the list is empty). */
export function pageRange(page, size, total) {
  if (!total) return { from: 0, to: 0 }
  const current = clampPage(page, total, size)
  return { from: (current - 1) * size + 1, to: Math.min(current * size, total) }
}

export const ELLIPSIS = '...'

/**
 * The numbered buttons: at most 7 items, page numbers and ELLIPSIS. First and last page are always there.
 * 12 pages: page 1 is 1 2 3 4 5 ... 12, page 6 is 1 ... 5 6 7 ... 12, page 12 is 1 ... 8 9 10 11 12.
 */
export function pageItems(page, pages) {
  if (pages <= 7) return Array.from({ length: pages }, (_, i) => i + 1)
  const current = Math.min(Math.max(page, 1), pages)
  if (current <= 4) return [1, 2, 3, 4, 5, ELLIPSIS, pages]
  if (current >= pages - 3) return [1, ELLIPSIS, pages - 4, pages - 3, pages - 2, pages - 1, pages]
  return [1, ELLIPSIS, current - 1, current, current + 1, ELLIPSIS, pages]
}

/** Page and size from a route query; a missing, malformed or unlisted value falls back to page 1 and the default size. */
export function readPaging(query, defaultSize = DEFAULT_PAGE_SIZE, sizes = PAGE_SIZES) {
  const page = Number(query?.page)
  const size = Number(query?.size)
  return {
    page: Number.isInteger(page) && page >= 1 ? page : 1,
    size: sizes.includes(size) ? size : defaultSize
  }
}

/** The query with page and size set: left out when they are the defaults (page 1, the default size). Other keys stay. */
export function pagingQuery(query, page, size, defaultSize = DEFAULT_PAGE_SIZE) {
  const { page: _page, size: _size, ...rest } = query || {}
  if (page > 1) rest.page = String(page)
  if (size !== defaultSize) rest.size = String(size)
  return rest
}
