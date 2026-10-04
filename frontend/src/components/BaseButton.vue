<template>
  <component :is="to ? 'router-link' : 'button'" v-bind="to ? { to } : { type }" :class="[BASE, SIZES[size], VARIANTS[variant]]">
    <slot />
  </component>
</template>

<script>
const BASE = 'inline-block cursor-pointer rounded-md border text-center align-middle font-sans font-medium leading-normal no-underline select-none transition-colors duration-[120ms] disabled:pointer-events-none disabled:opacity-65'
const SIZES = {
  sm: 'px-3 py-1 text-[0.9375rem]',
  md: 'px-4 py-2 text-base',
  lg: 'px-6 py-3 text-lg'
}
const VARIANTS = {
  primary: 'border-teal bg-teal text-paper hover:border-teal-hover hover:bg-teal-hover',
  secondary: 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint disabled:border-rule disabled:text-muted',
  danger: 'border-clay bg-clay text-paper hover:border-clay-hover hover:bg-clay-hover'
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
