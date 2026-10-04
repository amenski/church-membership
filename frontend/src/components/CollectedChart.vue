<template>
  <section class="min-w-0" aria-labelledby="collected-title">
    <SectionTitle id="collected-title">Collected by month</SectionTitle>
    <template v-if="error">
      <AlertBanner>The monthly amounts did not load.</AlertBanner>
      <TextButton @click="load">Try again</TextButton>
    </template>
    <ul v-else-if="hasCollected" class="m-0 grid list-none gap-1.5 p-0">
      <li
        v-for="row in rows"
        :key="row.month"
        class="grid grid-cols-[4.5rem_1fr_auto] items-center gap-x-3 text-xs sm:grid-cols-[5rem_1fr_9rem]"
      >
        <span class="text-muted">{{ row.label }}</span>
        <span class="block h-3" aria-hidden="true">
          <span
            :class="['block h-full min-w-px rounded-r', row.current ? 'bg-teal-line' : 'bg-teal']"
            :style="{ width: row.width }"
          ></span>
        </span>
        <span class="text-right tabular-nums text-ink">
          {{ formatMoney(row.amount) }}
          <span v-if="row.current" class="text-muted">in progress</span>
        </span>
      </li>
    </ul>
    <EmptyNote v-else-if="loaded">No payments in the last 12 months. Record one under Payments to see it here.</EmptyNote>
  </section>
</template>

<script>
import api from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import TextButton from '@/components/TextButton.vue'
import { formatMoney } from '@/utils'

// The last twelve months of payments, one bar a month, from GET /api/dashboard/collected-by-month.
// It loads itself; a parent that records a payment calls load() to refresh it. A failure shows a banner with Try again.
export default {
  name: 'CollectedChart',
  components: { AlertBanner, EmptyNote, SectionTitle, TextButton },
  setup() {
    return { formatMoney }
  },
  data() {
    return { collected: [], error: false, loaded: false }
  },
  computed: {
    hasCollected() {
      return this.collected.some(row => row.amount > 0)
    },
    // Oldest first, as the server sends it; the last row is the current month, still being collected
    rows() {
      const max = Math.max(...this.collected.map(row => row.amount), 0)
      return this.collected.map((row, index) => ({
        ...row,
        label: this.monthLabel(row.month),
        current: index === this.collected.length - 1,
        width: max > 0 ? `${(row.amount / max) * 100}%` : '0%'
      }))
    }
  },
  created() {
    return this.load()
  },
  methods: {
    async load() {
      try {
        this.collected = await api.getCollectedByMonth()
        this.error = false
      } catch (error) {
        console.error('Error loading collected by month:', error)
        this.error = true
      } finally {
        this.loaded = true
      }
    },
    monthLabel(month) {
      const [year, number] = month.split('-').map(Number)
      return new Date(year, number - 1, 1).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })
    }
  }
}
</script>
