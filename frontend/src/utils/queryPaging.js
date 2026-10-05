import { DEFAULT_PAGE_SIZE, PAGE_SIZES, clampPage, pagingQuery, readPaging } from '@/utils/paging'

// Keeps a list's page and size in the URL query (?page=2&size=50) so back and refresh keep the place.
// State lives in `page` and `pageSize`; every change is written with router.replace (no history entry), and a
// query change from outside (a link to the bare route) is read back. A screen with no router just keeps the state.
// A screen that holds other query keys of its own (Members' ?dues=) lists them in a `pagingExtraQuery()` method.
const sameQuery = (a, b) => {
  const keys = [...new Set([...Object.keys(a), ...Object.keys(b)])]
  return keys.every(key => String(a[key] ?? '') === String(b[key] ?? ''))
}

export function queryPaging({ defaultSize = DEFAULT_PAGE_SIZE, sizes = PAGE_SIZES } = {}) {
  return {
    data() {
      const { page, size } = readPaging(this.$route?.query, defaultSize, sizes)
      return { page, pageSize: size, pageSizes: sizes }
    },
    created() {
      // the route this state belongs to: a late change must never write a query onto the page the user just opened
      this.pagingPath = this.$route?.path
    },
    watch: {
      '$route.query.page'() {
        this.readPagingFromRoute()
      },
      '$route.query.size'() {
        this.readPagingFromRoute()
      }
    },
    methods: {
      readPagingFromRoute() {
        const { page, size } = readPaging(this.$route?.query, defaultSize, sizes)
        this.page = page
        this.pageSize = size
      },
      setPage(page) {
        this.page = page
        this.writePaging()
      },
      setPageSize(size) {
        this.pageSize = size
        this.page = 1
        this.writePaging()
      },
      // A filter, search or sort changed: back to page 1 (the write also carries the screen's own query keys)
      resetPage() {
        this.page = 1
        this.writePaging()
      },
      // The list has loaded or changed length: a page beyond the last becomes the last
      settlePage(total) {
        const page = clampPage(this.page, total, this.pageSize)
        if (page !== this.page) this.setPage(page)
      },
      writePaging() {
        const route = this.$route
        if (!this.$router || !route || route.path !== this.pagingPath) return
        const query = { ...pagingQuery(route.query, this.page, this.pageSize, defaultSize), ...this.pagingExtraQuery?.() }
        for (const key of Object.keys(query)) if (query[key] === undefined) delete query[key]
        if (!sameQuery(query, route.query)) this.$router.replace({ query })
      }
    }
  }
}
