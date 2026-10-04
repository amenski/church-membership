<template>
  <div :class="['mb-4 rounded-sm border px-3 py-2 text-sm', TONES[tone]]" role="alert">
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
