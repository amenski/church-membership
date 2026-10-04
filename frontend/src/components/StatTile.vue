<template>
  <div :class="['rounded-md border border-rule bg-paper px-3', slim ? 'py-1.5' : 'py-2.5']">
    <dt class="text-xs font-medium text-muted">{{ label }}</dt>
    <dd :class="['m-0 flex flex-wrap items-baseline gap-x-1.5', slim ? 'mt-0' : 'mt-1']">
      <span :class="[slim ? 'text-xl' : 'text-2xl', 'font-semibold tabular-nums', TONES[tone]]">{{ value }}</span>
      <span v-if="hint" class="text-xs text-muted">{{ hint }}</span>
      <router-link v-if="to" :to="to" class="text-xs">{{ toLabel }}</router-link>
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
    // an optional link after the hint, e.g. "Review"
    to: { type: String, default: '' },
    toLabel: { type: String, default: 'Review' },
    // a slimmer tile for a facts strip
    slim: { type: Boolean, default: false },
    tone: { type: String, default: 'default', validator: value => value in TONES }
  },
  data() {
    return { TONES }
  }
}
</script>
