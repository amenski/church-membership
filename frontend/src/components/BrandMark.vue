<template>
  <router-link
    :to="to"
    :aria-label="$t('nav.homeLabel')"
    :class="['flex no-underline', inline ? 'flex-row items-baseline gap-3' : 'flex-col']"
  >
    <span :class="['font-ethiopic font-bold leading-[1.3] text-teal', inline ? 'text-lg' : 'text-xl']">ፈለገ ሰላም</span>
    <span class="text-sm leading-tight font-semibold text-ink">Felege Selam</span>
  </router-link>
</template>

<script>
// The wordmark as a link: stacked in the rail, on one line in the top bar
export default {
  name: 'BrandMark',
  props: {
    to: { type: String, required: true },
    inline: { type: Boolean, default: false }
  }
}
</script>
