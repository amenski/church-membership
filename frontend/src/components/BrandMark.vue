<template>
  <router-link
    :to="to"
    aria-label="Felege Selam home"
    :class="['tw:flex tw:no-underline', inline ? 'tw:flex-row tw:items-baseline tw:gap-3' : 'tw:flex-col']"
  >
    <span :class="['tw:font-ethiopic tw:font-bold tw:leading-[1.3] tw:text-teal', inline ? 'tw:text-lg' : 'tw:text-xl']">ፈለገ ሰላም</span>
    <span class="tw:font-display tw:text-lg tw:leading-[1.2] tw:font-bold tw:text-ink">Felege Selam</span>
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
