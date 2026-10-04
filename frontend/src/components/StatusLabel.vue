<template>
  <span :class="['inline-flex items-center gap-1.5 text-sm font-medium', TONES[tone]]">
    <span class="size-1.5 shrink-0 rounded-full bg-current" aria-hidden="true"></span>
    <slot />
  </span>
</template>

<script>
// A status is a dot plus a word, never colour alone
const TONES = {
  paid: 'text-fern-text',
  behind: 'text-ochre-text',
  danger: 'text-clay',
  muted: 'text-muted'
}

export default {
  name: 'StatusLabel',
  props: {
    tone: { type: String, required: true, validator: value => value in TONES }
  },
  data() {
    return { TONES }
  }
}
</script>
