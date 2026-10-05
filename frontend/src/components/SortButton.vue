<template>
  <!-- The button in a sortable column header; the <th> carries aria-sort (utils/paymentQuery.js ariaSort), the icon only shows it -->
  <button type="button" :class="SORT_BUTTON">
    {{ label }}
    <Icon :name="icon" :size="12" :class="sorted ? 'text-ink' : 'text-muted'" />
  </button>
</template>

<script>
import Icon from '@/components/Icon.vue'
import { SORT_BUTTON } from '@/ui/classes'

// `state` is the column's aria-sort value: 'none', 'ascending' or 'descending'
export default {
  name: 'SortButton',
  components: { Icon },
  props: {
    label: { type: String, required: true },
    state: { type: String, default: 'none' }
  },
  setup() {
    return { SORT_BUTTON }
  },
  computed: {
    sorted() {
      return this.state !== 'none'
    },
    icon() {
      if (!this.sorted) return 'chevrons-up-down'
      return this.state === 'ascending' ? 'caret-up' : 'caret-down'
    }
  }
}
</script>
