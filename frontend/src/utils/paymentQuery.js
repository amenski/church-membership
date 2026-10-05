import { PAYMENT_METHODS } from '@/utils/paymentPayload'

// The Payments history is paged by the server (GET /api/payments/page). This is the mapping between what the screen
// holds (page, size, search, method, sort), the URL (?page=&size=&search=&method=&sort=) and the request:
// the pure, debounce-free parts. page and size in the URL are utils/queryPaging.js's.

export const DEFAULT_SORT = { field: 'paymentDate', direction: 'desc' }

// Column header -> server sort field, the direction the first click gives, and the words for "sorted by ..."
export const SORT_FIELDS = {
  paymentDate: { first: 'desc', labelKey: 'payments.colPaidOn', ascKey: 'payments.sortOldestFirst', descKey: 'payments.sortNewestFirst' },
  member: { first: 'asc', labelKey: 'payments.colMember', ascKey: 'payments.sortAToZ', descKey: 'payments.sortZToA' },
  period: { first: 'desc', labelKey: 'payments.colMonth', ascKey: 'payments.sortOldestFirst', descKey: 'payments.sortNewestFirst' },
  amount: { first: 'desc', labelKey: 'payments.colAmount', ascKey: 'payments.sortLowestFirst', descKey: 'payments.sortHighestFirst' }
}

/** "member,desc" (the server's sort parameter) -> { field, direction }; anything else is the default sort. */
export function parseSort(value) {
  const [field, direction, extra] = typeof value === 'string' ? value.split(',') : []
  if (!Object.hasOwn(SORT_FIELDS, field) || extra !== undefined || !['asc', 'desc'].includes(direction)) return { ...DEFAULT_SORT }
  return { field, direction }
}

/** { field, direction } -> "member,desc". */
export function formatSort(sort) {
  return `${sort.field},${sort.direction}`
}

/** A click on a column header: the sorted column flips, another column starts in its natural direction. */
export function nextSort(sort, field) {
  if (sort.field === field) return { field, direction: sort.direction === 'asc' ? 'desc' : 'asc' }
  return { field, direction: SORT_FIELDS[field].first }
}

/** The `aria-sort` of a column header. */
export function ariaSort(sort, field) {
  if (sort.field !== field) return 'none'
  return sort.direction === 'asc' ? 'ascending' : 'descending'
}

/**
 * "Paid on, newest first": for the table caption and footer.
 * @param {{field: string, direction: string}} sort
 * @param {Function} t useI18n's t
 */
export function sortLabel(sort, t) {
  const { labelKey, ascKey, descKey } = SORT_FIELDS[sort.field]
  return t('payments.sortedBy', { column: t(labelKey), order: t(sort.direction === 'asc' ? ascKey : descKey) })
}

const first = (value) => (Array.isArray(value) ? value[0] : value)

/** Search, method and sort from a route query. A missing or unknown value is no search, any method, the default sort. */
export function readFilters(query) {
  const method = String(first(query?.method) ?? '').toUpperCase()
  const search = first(query?.search)
  return {
    search: typeof search === 'string' ? search.trim() : '',
    method: PAYMENT_METHODS.some(entry => entry.value === method) ? method : '',
    sort: parseSort(first(query?.sort))
  }
}

/** The query keys for the filters: undefined (left out of the URL) when it is the default. */
export function filtersQuery({ search, method, sort }) {
  const text = search.trim()
  return {
    search: text || undefined,
    method: method || undefined,
    sort: sort.field === DEFAULT_SORT.field && sort.direction === DEFAULT_SORT.direction ? undefined : formatSort(sort)
  }
}

/** The request for GET /payments/page: the screen's page is 1-based, the server's 0-based; empty search and method are left out. */
export function pageParams({ page, size, search, method, sort }) {
  const params = { page: Math.max(page, 1) - 1, size, sort: formatSort(sort) }
  const text = search.trim()
  if (text) params.search = text
  if (method) params.method = method
  return params
}
