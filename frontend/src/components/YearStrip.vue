<template>
  <div role="group" :aria-label="label" :class="['flex', ledger ? 'gap-1' : large ? 'gap-[3px]' : 'gap-0.5']">
    <div v-for="cell in cells" :key="cell.month" :class="['flex flex-col items-center', large && 'gap-[3px]']">
      <span :class="[BOX, ledger ? 'size-7 rounded-sm' : large ? 'h-[26px] w-5 rounded-[3px]' : 'h-[18px] w-2.5 rounded-[2px]', (muted ? MUTED_SQUARES : SQUARES)[cell.state]]" aria-hidden="true"></span>
      <span v-if="large" class="text-xs leading-none text-muted" aria-hidden="true">{{ cell.initial }}</span>
      <span class="sr-only">{{ cell.name }}: {{ cell.label }}</span>
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
    size: { type: String, default: 'compact', validator: value => ['compact', 'large', 'ledger'].includes(value) },
    label: { type: String, default: 'Dues, last 12 months' }
  },
  data() {
    return { BOX, SQUARES, MUTED_SQUARES }
  },
  computed: {
    // 'large' is 20x26px squares with the month initial under each; 'ledger' is 28px squares, the month names
    // come from a header row the parent draws above the column (the Overview's "Dues by month" grid)
    large() {
      return this.size === 'large'
    },
    ledger() {
      return this.size === 'ledger'
    },
    cells() {
      return stripCells({
        currentMonth: this.currentMonth,
        joinDate: this.joinDate,
        paidMonths: this.paidMonths,
        monthsMissed: this.monthsMissed,
        countsForDues: this.countsForDues
      })
    }
  }
}
</script>
