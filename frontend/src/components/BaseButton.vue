<template>
  <component :is="to ? 'router-link' : 'button'" v-bind="to ? { to } : { type }" :class="[BASE, SIZES[size], heightClass, VARIANTS[variant]]">
    <slot />
  </component>
</template>

<script>
const BASE = 'inline-flex items-center justify-center cursor-pointer rounded-md border text-center align-middle font-sans font-medium leading-normal no-underline select-none transition-colors duration-[120ms] disabled:pointer-events-none disabled:opacity-65'
const SIZES = {
  sm: 'px-3 py-1 text-[0.9375rem]',
  md: 'px-4 py-2 text-base',
  lg: 'px-6 py-3 text-lg'
}
// The density height: a main action (primary, danger) is 48px when comfortable, the rest 44px.
// min-height never shrinks a button, so dense screens (natural height ~42px) are unchanged.
// Not applied to size="sm": compact buttons keep their padding size. Dialogs render outside
// data-density, so their buttons take the dense (root) height and stay as they are.
const HEIGHTS = {
  primary: 'min-h-(--control-primary-h)',
  secondary: 'min-h-(--control-h)',
  danger: 'min-h-(--control-primary-h)'
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
  computed: {
    // One height class per button, so the two min-height utilities never compete in the class list
    heightClass() {
      return this.size === 'sm' ? '' : HEIGHTS[this.variant]
    }
  },
  data() {
    return { BASE, SIZES, VARIANTS }
  }
}
</script>
