<template>
  <section :class="[CARD, 'min-w-0']" aria-labelledby="collected-title">
    <template v-if="error">
      <SectionTitle id="collected-title" class="!mb-2 !text-xl">{{ $t('chart.title') }}</SectionTitle>
      <AlertBanner>{{ $t('chart.notLoaded') }}</AlertBanner>
      <TextButton @click="load">{{ $t('common.tryAgain') }}</TextButton>
    </template>
    <template v-else-if="hasCollected">
      <div class="flex flex-wrap items-end justify-between gap-x-6 gap-y-2">
        <div>
          <SectionTitle id="collected-title" class="!mb-0.5 !text-xl">{{ $t('chart.title') }}</SectionTitle>
          <p class="m-0 max-w-[60ch] text-sm text-muted">{{ $t('chart.caption', { range }) }}</p>
        </div>
        <p class="m-0 text-sm text-muted sm:text-right">
          {{ $t('chart.twelveMonths') }}<br><span class="text-2xl font-semibold tabular-nums text-ink">{{ formatMoney(total) }}</span>
        </p>
      </div>

      <!-- The columns are drawn for the eye; the table below says the same in words -->
      <ol class="m-0 mt-5 grid h-48 list-none grid-cols-12 items-end gap-x-0.5 border-b border-field p-0 pt-2 sm:gap-x-3" aria-hidden="true">
        <li v-for="row in rows" :key="row.month" class="flex h-full flex-col items-stretch justify-end">
          <span class="mb-1 text-center text-[10px] font-semibold leading-none tabular-nums whitespace-nowrap text-ink md:text-xs">
            <span class="md:hidden">{{ row.short }}</span><span class="max-md:hidden">{{ row.whole }}</span>
          </span>
          <span :class="['block rounded-t-sm', row.current ? CURRENT_COLUMN : 'bg-teal']" :style="{ height: row.height }"></span>
        </li>
      </ol>
      <ol class="m-0 grid list-none grid-cols-12 gap-x-0.5 p-0 pt-1.5 sm:gap-x-3" aria-hidden="true">
        <li v-for="row in rows" :key="row.month" class="text-center text-[11px] font-medium text-muted md:text-xs">{{ row.label }}</li>
      </ol>
      <p class="mt-3 mb-0 flex items-center gap-1.5 text-xs text-muted">
        <span :class="['box-border block h-3.5 w-3.5 rounded-sm', CURRENT_COLUMN]" aria-hidden="true"></span>
        {{ $t('chart.currentInProgress', { month: currentName }) }}
      </p>

      <table class="sr-only">
        <caption>{{ $t('chart.tableCaption') }}</caption>
        <thead>
          <tr><th scope="col">{{ $t('chart.colMonth') }}</th><th scope="col">{{ $t('chart.colCollected') }}</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.month">
            <th scope="row">{{ row.name }}</th>
            <td>{{ formatMoney(row.amount) }}{{ row.current ? $t('chart.inProgress') : '' }}</td>
          </tr>
        </tbody>
      </table>
    </template>
    <template v-else>
      <SectionTitle id="collected-title" class="!mb-2 !text-xl">{{ $t('chart.title') }}</SectionTitle>
      <EmptyNote v-if="loaded">{{ $t('chart.none') }}</EmptyNote>
    </template>
  </section>
</template>

<script>
import api from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import TextButton from '@/components/TextButton.vue'
import { formatDate, formatMoney } from '@/utils'
import { CARD } from '@/ui/classes'

// The tallest column, in px; the other months scale to the largest one
const MAX_COLUMN = 150
// The current month: hatched teal-line on paper, outlined in teal (it is still being collected)
const CURRENT_COLUMN = 'box-border border-2 border-b-0 border-teal bg-[repeating-linear-gradient(135deg,var(--color-paper)_0_4px,var(--color-teal-line)_4px_6px)]'

const dollars = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })
// Whole dollars on a phone are too wide for 12 columns: $1,250 becomes $1.3k
const shortMoney = (amount) => {
  const rounded = Math.round(amount)
  return rounded < 1000 ? dollars.format(rounded) : `$${(rounded / 1000).toFixed(1).replace(/\.0$/, '')}k`
}
const monthStart = (month) => {
  const [year, number] = month.split('-').map(Number)
  return new Date(year, number - 1, 1)
}

// The last twelve months of payments, one column a month, from GET /api/dashboard/collected-by-month.
// It loads itself; a parent that records a payment calls load() to refresh it. A failure shows a banner with Try again.
export default {
  name: 'CollectedChart',
  components: { AlertBanner, EmptyNote, SectionTitle, TextButton },
  setup() {
    return { CARD, CURRENT_COLUMN, formatMoney }
  },
  data() {
    return { collected: [], error: false, loaded: false }
  },
  computed: {
    hasCollected() {
      return this.collected.some(row => row.amount > 0)
    },
    total() {
      return this.collected.reduce((sum, row) => sum + row.amount, 0)
    },
    // "November 2025 to October 2026"
    range() {
      const first = this.collected[0]
      const last = this.collected[this.collected.length - 1]
      return this.$t('strip.range', { from: this.longName(first.month), to: this.longName(last.month) })
    },
    currentName() {
      return this.longName(this.collected[this.collected.length - 1].month)
    },
    // Oldest first, as the server sends it; the last row is the current month, still being collected
    rows() {
      const max = Math.max(...this.collected.map(row => row.amount), 0)
      return this.collected.map((row, index) => ({
        ...row,
        label: formatDate(`${row.month}-01`, 'MMM'),
        name: this.longName(row.month),
        whole: dollars.format(row.amount),
        short: shortMoney(row.amount),
        current: index === this.collected.length - 1,
        height: `${row.amount > 0 && max > 0 ? Math.max(1, Math.round((row.amount / max) * MAX_COLUMN)) : 0}px`
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
    longName(month) {
      return formatDate(`${month}-01`, 'MMMM yyyy')
    }
  }
}
</script>
