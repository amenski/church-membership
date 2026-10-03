<template>
  <div :class="['tw:mb-4 tw:rounded-md tw:border tw:px-4 tw:py-3 tw:text-base', TONES[tone]]" role="alert">
    <slot />
  </div>
</template>

<script>
const TONES = {
  danger: 'tw:border-clay-line tw:bg-clay-tint tw:text-clay',
  success: 'tw:border-fern-line tw:bg-fern-tint tw:text-fern-text',
  warning: 'tw:border-ochre-line tw:bg-ochre-tint tw:text-ochre-text',
  info: 'tw:border-teal-line tw:bg-teal-tint tw:text-teal'
}

export default {
  name: 'AlertBanner',
  props: {
    tone: { type: String, default: 'danger', validator: value => value in TONES }
  },
  data() {
    return { TONES }
  }
}
</script>
