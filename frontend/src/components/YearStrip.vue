<template>
  <!-- detail: the member's page, rows of 12 big squares with the month name above each, a caption over every row -->
  <div v-if="detail" role="group" :aria-label="label || $t('strip.duesLast12')" class="flex flex-col gap-4">
    <div v-for="row in rows" :key="row[0].month">
      <div v-if="rows.length > 1" class="mb-1.5 text-sm font-medium text-ink" aria-hidden="true">{{ $t('strip.range', { from: row[0].name, to: row[row.length - 1].name }) }}</div>
      <div class="flex flex-wrap gap-1">
        <div v-for="cell in row" :key="cell.month" class="flex w-11 flex-col items-center gap-1">
          <span class="text-xs leading-none text-muted" aria-hidden="true">{{ cell.short }}</span>
          <span :class="[BOX, 'h-9 w-11 rounded-sm', (muted ? MUTED_SQUARES : SQUARES)[cell.state]]" aria-hidden="true"></span>
          <span class="sr-only">{{ cell.name }}: {{ $t(cell.labelKey) }}</span>
        </div>
      </div>
    </div>
  </div>
  <div v-else role="group" :aria-label="label || $t('strip.duesLast12')" :class="['flex', ledger ? 'gap-1' : large ? 'gap-[3px]' : 'gap-0.5']">
    <div v-for="cell in cells" :key="cell.month" :class="['flex flex-col items-center', large && 'gap-[3px]']">
      <span :class="[BOX, ledger ? 'size-7 rounded-sm' : large ? 'h-[26px] w-5 rounded-[3px]' : 'h-[18px] w-2.5 rounded-[2px]', (muted ? MUTED_SQUARES : SQUARES)[cell.state]]" aria-hidden="true"></span>
      <span v-if="large" class="text-xs leading-none text-muted" aria-hidden="true">{{ cell.initial }}</span>
      <span class="sr-only">{{ cell.name }}: {{ $t(cell.labelKey) }}</span>
    </div>
  </div>
</template>

<script>
import { SQUARES, stripCells } from '@/utils/yearStrip'

const BOX = 'box-border block'
// The archived treatment: a paid month is a quiet grey (the field edge colour at half strength); nothing red or amber
const MUTED_SQUARES = { ...SQUARES, paid: 'bg-field/50', missed: SQUARES.none, due: SQUARES.none }

export default {
  name: 'YearStrip',
  props: {
    joinDate: { type: String, default: '' },
    paidMonths: { type: Set, required: true },
    currentMonth: { type: String, required: true },
    monthsMissed: { type: Number, default: 0 },
    countsForDues: { type: Boolean, default: true },
    muted: { type: Boolean, default: false },
    size: { type: String, default: 'compact', validator: value => ['compact', 'large', 'ledger', 'detail'].includes(value) },
    // how many months, ending with currentMonth; the detail size draws them 12 to a row
    months: { type: Number, default: 12 },
    // the caller's aria-label; blank falls back to the generic one for this language
    label: { type: String, default: '' }
  },
  data() {
    return { BOX, SQUARES, MUTED_SQUARES }
  },
  computed: {
    // 'detail' is 44x36px squares with the month name above, 12 to a row (a member's page: 24 months, two rows)
    // 'large' is 20x26px squares with the month initial under each; 'ledger' is 28px squares, the month names
    // come from a header row the parent draws above the column (the Overview's "Dues by month" grid)
    large() {
      return this.size === 'large'
    },
    ledger() {
      return this.size === 'ledger'
    },
    detail() {
      return this.size === 'detail'
    },
    rows() {
      const rows = []
      for (let i = 0; i < this.cells.length; i += 12) rows.push(this.cells.slice(i, i + 12))
      return rows
    },
    cells() {
      return stripCells({
        currentMonth: this.currentMonth,
        joinDate: this.joinDate,
        paidMonths: this.paidMonths,
        monthsMissed: this.monthsMissed,
        countsForDues: this.countsForDues,
        count: this.months
      })
    }
  }
}
</script>
