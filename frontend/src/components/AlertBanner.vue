<template>
  <div :class="['mb-4 rounded-md border px-4 py-3 text-base', TONES[tone]]" role="alert">
    <slot />
  </div>
</template>

<script>
const TONES = {
  danger: 'border-clay-line bg-clay-tint text-clay',
  success: 'border-fern-line bg-fern-tint text-fern-text',
  warning: 'border-ochre-line bg-ochre-tint text-ochre-text',
  info: 'border-teal-line bg-teal-tint text-teal'
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
