<template>
  <svg
    class="woven-band"
    width="100%"
    :height="height"
    aria-hidden="true"
    focusable="false"
    preserveAspectRatio="none"
  >
    <defs>
      <!-- 8 x 8 tile: ink and ochre hairlines, a teal field, one paper diamond with an ochre heart -->
      <pattern :id="patternId" width="8" height="8" patternUnits="userSpaceOnUse" :patternTransform="`scale(${height / 8})`">
        <rect width="8" height="8" fill="#0E6B6E" />
        <rect width="8" height="1" fill="#14323A" />
        <rect y="7" width="8" height="1" fill="#14323A" />
        <rect y="1" width="8" height="1" fill="#C98A1B" />
        <rect y="6" width="8" height="1" fill="#C98A1B" />
        <polygon points="4,2 7,4 4,6 1,4" fill="#FFFFFF" />
        <rect x="3.25" y="3.25" width="1.5" height="1.5" fill="#C98A1B" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" :fill="`url(#${patternId})`" />
  </svg>
</template>

<script>
let nextId = 0

export default {
  name: 'WovenBand',
  props: {
    height: { type: Number, default: 8 }
  },
  data() {
    return { patternId: `woven-band-${nextId++}` }
  }
}
</script>

<style scoped>
.woven-band {
  display: block;
}
</style>
