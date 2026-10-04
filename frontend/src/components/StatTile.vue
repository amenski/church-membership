<template>
  <div class="rounded-md border border-rule bg-paper px-3 py-2.5">
    <dt class="text-xs font-medium text-muted">{{ label }}</dt>
    <dd class="m-0 mt-1 flex flex-wrap items-baseline gap-x-1.5">
      <span :class="['text-2xl font-semibold tabular-nums', TONES[tone]]">{{ value }}</span>
      <span v-if="hint" class="text-xs text-muted">{{ hint }}</span>
    </dd>
  </div>
</template>

<script>
// One figure in the Overview's stat row. A <div> holding a dt/dd pair, so it must sit inside a <dl>.
const TONES = {
  default: 'text-ink',
  paid: 'text-fern-text',
  behind: 'text-ochre-text',
  danger: 'text-clay'
}

export default {
  name: 'StatTile',
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number], required: true },
    hint: { type: String, default: '' },
    tone: { type: String, default: 'default', validator: value => value in TONES }
  },
  data() {
    return { TONES }
  }
}
</script>
