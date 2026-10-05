<template>
  <!-- Hidden when the whole list fits in the smallest page size. With more rows than that the count line and the "Rows per page"
       select always show (a list that fits the current size must still be able to go back to a smaller one); the buttons only
       when the list needs more than one page. Works the same for a list in the browser and one the server pages: it only
       needs the total. The caller sets the padding (class falls through): a card footer is px-4 py-3, a list outside a card has none -->
  <nav v-if="total > smallest" aria-label="Pagination" class="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 text-sm text-muted">
    <p class="m-0 tabular-nums" aria-live="polite">Showing {{ range.from }} to {{ range.to }} of {{ total }}</p>

    <div class="flex flex-wrap items-center gap-x-4 gap-y-3 max-sm:w-full max-sm:justify-between">
      <div class="flex items-center gap-2">
        <label :for="`${uid}-size`" class="whitespace-nowrap">Rows per page</label>
        <select
          :id="`${uid}-size`"
          :value="pageSize"
          class="h-11 rounded-sm border border-field bg-paper px-2 text-lg text-ink focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal lg:h-(--control-h) lg:text-sm"
          @change="$emit('update:pageSize', Number($event.target.value))"
        >
          <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option>
        </select>
      </div>

      <ul v-if="total > pageSize" class="m-0 flex list-none flex-wrap items-center gap-1 p-0">
        <li>
          <button type="button" :class="BUTTON" :disabled="current <= 1" @click="go(current - 1)">Previous</button>
        </li>
        <!-- below sm, and in a compact pager, the numbers give way to "Page 4 of 12": seven 44px buttons do not fit a phone -->
        <template v-for="(item, index) in items" :key="`${item}-${index}`">
          <li v-if="item === ELLIPSIS" :class="['px-1', numbered]" aria-hidden="true">&hellip;</li>
          <li v-else :class="numbered">
            <button
              type="button"
              :class="[BUTTON, 'tabular-nums', item === current ? 'border-teal bg-teal text-paper hover:bg-teal-hover' : '']"
              :aria-label="`Page ${item}`"
              :aria-current="item === current ? 'page' : undefined"
              @click="go(item)"
            >{{ item }}</button>
          </li>
        </template>
        <li :class="['px-2 tabular-nums', compact ? '' : 'sm:hidden']">Page {{ current }} of {{ pages }}</li>
        <li>
          <button type="button" :class="BUTTON" :disabled="current >= pages" @click="go(current + 1)">Next</button>
        </li>
      </ul>
    </div>
  </nav>
</template>

<script>
import { useId } from 'vue'
import { ELLIPSIS, PAGE_SIZES, clampPage, pageItems, pageRange, totalPages } from '@/utils/paging'

// One page button: 44px below lg, the control height from lg; the current page is the filled one (the segmented control's active look)
const BUTTON = 'inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-sm border border-field bg-paper px-3 text-base font-medium text-ink hover:border-teal hover:bg-teal-tint disabled:pointer-events-none disabled:border-rule disabled:text-muted disabled:opacity-65 lg:min-h-(--control-h) lg:min-w-(--control-h) lg:px-2.5 lg:text-sm'

// A page bar. A list in the browser is sliced by the caller (utils/paging.js); a list the server pages passes the server's total.
// Either way the caller owns page and pageSize.
export default {
  name: 'Pager',
  props: {
    page: { type: Number, required: true },
    pageSize: { type: Number, required: true },
    total: { type: Number, required: true },
    pageSizes: { type: Array, default: () => PAGE_SIZES },
    // for a dialog: "Page 4 of 12" in place of the numbered buttons
    compact: { type: Boolean, default: false }
  },
  emits: ['update:page', 'update:pageSize'],
  setup() {
    return { uid: useId(), BUTTON, ELLIPSIS }
  },
  computed: {
    smallest() {
      return Math.min(...this.pageSizes)
    },
    pages() {
      return totalPages(this.total, this.pageSize)
    },
    // a page beyond the last shows as the last
    current() {
      return clampPage(this.page, this.total, this.pageSize)
    },
    range() {
      return pageRange(this.current, this.pageSize, this.total)
    },
    items() {
      return pageItems(this.current, this.pages)
    },
    numbered() {
      return this.compact ? 'hidden' : 'max-sm:hidden'
    }
  },
  methods: {
    go(page) {
      if (page !== this.current && page >= 1 && page <= this.pages) this.$emit('update:page', page)
    }
  }
}
</script>
