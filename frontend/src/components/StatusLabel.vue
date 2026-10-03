<template>
  <span :class="['tw:inline-flex tw:items-center tw:gap-2 tw:font-medium', TONES[tone]]">
    <span class="tw:size-2 tw:shrink-0 tw:rounded-full tw:bg-current" aria-hidden="true"></span>
    <slot />
  </span>
</template>

<script>
// A status is a dot plus a word, never colour alone
const TONES = {
  paid: 'tw:text-fern-text',
  behind: 'tw:text-ochre-text',
  inactive: 'tw:text-clay'
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
