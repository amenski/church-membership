<template>
  <div role="group" :aria-label="label" :class="['flex', large ? 'gap-[3px]' : 'gap-0.5']">
    <div v-for="cell in cells" :key="cell.month" :class="['flex flex-col items-center', large && 'gap-[3px]']">
      <span :class="[BOX, large ? 'h-[26px] w-5 rounded-[3px]' : 'h-[18px] w-2.5 rounded-[2px]', SQUARES[cell.state]]" aria-hidden="true"></span>
      <span v-if="large" class="text-xs leading-none text-muted" aria-hidden="true">{{ cell.initial }}</span>
      <span class="sr-only">{{ cell.name }}: {{ cell.label }}</span>
    </div>
  </div>
</template>

<script>
import { stripCells } from '@/utils/yearStrip'

const BOX = 'box-border block'
// Tokens only: teal paid, clay hatching with a clay edge missed, ochre outline due now, dashed field edge for the rest
const SQUARES = {
  paid: 'bg-teal',
  missed: 'border border-clay bg-[repeating-linear-gradient(135deg,var(--color-clay-tint)_0_2px,var(--color-clay)_2px_4px)]',
  due: 'border-2 border-ochre-edge bg-paper',
  none: 'border border-dashed border-field',
  uncounted: 'border border-dashed border-field'
}

export default {
  name: 'YearStrip',
  props: {
    joinDate: { type: String, default: '' },
    paidMonths: { type: Set, required: true },
    currentMonth: { type: String, required: true },
    monthsMissed: { type: Number, default: 0 },
    countsForDues: { type: Boolean, default: true },
    size: { type: String, default: 'compact', validator: value => ['compact', 'large'].includes(value) },
    label: { type: String, default: 'Dues, last 12 months' }
  },
  data() {
    return { BOX, SQUARES }
  },
  computed: {
    large() {
      return this.size === 'large'
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
