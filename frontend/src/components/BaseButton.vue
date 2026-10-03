<template>
  <component :is="to ? 'router-link' : 'button'" v-bind="to ? { to } : { type }" :class="[BASE, SIZES[size], VARIANTS[variant]]">
    <slot />
  </component>
</template>

<script>
const BASE = 'tw:inline-block tw:cursor-pointer tw:rounded-md tw:border tw:text-center tw:align-middle tw:font-sans tw:font-medium tw:leading-normal tw:no-underline tw:select-none tw:transition-colors tw:duration-[120ms] tw:disabled:pointer-events-none tw:disabled:opacity-65'
const SIZES = {
  sm: 'tw:px-3 tw:py-1 tw:text-[0.9375rem]',
  md: 'tw:px-4 tw:py-2 tw:text-base',
  lg: 'tw:px-6 tw:py-3 tw:text-lg'
}
const VARIANTS = {
  primary: 'tw:border-teal tw:bg-teal tw:text-paper tw:hover:border-teal-hover tw:hover:bg-teal-hover',
  secondary: 'tw:border-field tw:bg-paper tw:text-ink tw:hover:border-teal tw:hover:bg-teal-tint tw:disabled:border-rule tw:disabled:text-muted',
  danger: 'tw:border-clay tw:bg-clay tw:text-paper tw:hover:border-clay-hover tw:hover:bg-clay-hover'
}

export default {
  name: 'BaseButton',
  props: {
    variant: { type: String, default: 'primary', validator: value => value in VARIANTS },
    size: { type: String, default: 'md', validator: value => value in SIZES },
    type: { type: String, default: 'button' },
    // a route: renders a link that looks like the button
    to: { type: [String, Object], default: null }
  },
  data() {
    return { BASE, SIZES, VARIANTS }
  }
}
</script>
