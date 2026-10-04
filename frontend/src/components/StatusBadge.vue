<template>
  <span :class="['inline-block rounded-sm border px-2 py-0.5 text-xs font-medium whitespace-nowrap', TONES[tone]]">
    <slot />
  </span>
</template>

<script>
// A count or a state on a row ("3 delivered", "Skipped, no email"): the word is always in the badge, colour only backs it
const TONES = {
  paid: 'border-fern-line bg-fern-tint text-fern-text',
  behind: 'border-ochre-line bg-ochre-tint text-ochre-text',
  danger: 'border-clay-line bg-clay-tint text-clay',
  muted: 'border-rule bg-mist text-muted'
}

export default {
  name: 'StatusBadge',
  props: {
    tone: { type: String, required: true, validator: value => value in TONES }
  },
  data() {
    return { TONES }
  }
}
</script>
