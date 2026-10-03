<template>
  <Teleport to="body">
    <Transition
      enter-active-class="tw:motion-safe:transition tw:motion-safe:duration-150 tw:motion-safe:ease-out"
      enter-from-class="tw:opacity-0"
      leave-active-class="tw:motion-safe:transition tw:motion-safe:duration-100 tw:motion-safe:ease-in"
      leave-to-class="tw:opacity-0"
    >
      <div
        v-if="modelValue"
        class="tw:fixed tw:inset-0 tw:z-[1200] tw:flex tw:items-center tw:justify-center tw:bg-ink/40 tw:p-4"
        data-modal-backdrop
        @mousedown.self="pressedOnBackdrop = true"
        @click.self="onBackdropClick"
      >
        <div
          ref="panel"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
          :class="[
            'tw:flex tw:max-h-[calc(100dvh-2rem)] tw:w-full tw:flex-col tw:rounded-md tw:border tw:border-rule tw:bg-paper tw:text-ink tw:shadow-modal',
            WIDTH[size] || WIDTH.md
          ]"
          @keydown="onKeydown"
        >
          <div class="tw:flex tw:items-start tw:justify-between tw:gap-4 tw:border-b tw:border-rule tw:px-6 tw:py-4">
            <h2 :id="titleId" class="tw:m-0 tw:font-display tw:text-xl tw:leading-tight tw:font-bold tw:text-ink">{{ title }}</h2>
            <button
              type="button"
              class="tw:-mr-2 tw:-mt-1 tw:flex tw:size-9 tw:shrink-0 tw:cursor-pointer tw:items-center tw:justify-center tw:rounded-md tw:border-0 tw:bg-transparent tw:text-muted tw:hover:text-ink"
              aria-label="Close"
              @click="close"
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <path d="M3 3l10 10M13 3L3 13" />
              </svg>
            </button>
          </div>
          <div class="tw:overflow-y-auto tw:p-6">
            <slot />
          </div>
          <div v-if="$slots.footer" class="tw:flex tw:flex-wrap tw:justify-end tw:gap-2 tw:border-t tw:border-rule tw:px-6 tw:py-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script>
const WIDTH = { sm: 'tw:max-w-sm', md: 'tw:max-w-lg', lg: 'tw:max-w-3xl' }
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
let nextId = 0

export default {
  name: 'BaseModal',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, required: true },
    size: { type: String, default: 'md', validator: value => ['sm', 'md', 'lg'].includes(value) }
  },
  emits: ['update:modelValue'],
  data() {
    return {
      titleId: `base-modal-title-${nextId++}`,
      pressedOnBackdrop: false,
      previouslyFocused: null,
      savedOverflow: '',
      savedPaddingRight: '',
      WIDTH
    }
  },
  watch: {
    modelValue: {
      immediate: true,
      handler(open) {
        if (open) this.onOpen()
        else this.onClose()
      }
    }
  },
  beforeUnmount() {
    if (this.modelValue) this.onClose()
  },
  methods: {
    close() {
      this.$emit('update:modelValue', false)
    },
    onBackdropClick() {
      // a drag that starts inside the panel and ends on the backdrop is not a click on it
      const intended = this.pressedOnBackdrop
      this.pressedOnBackdrop = false
      if (intended) this.close()
    },
    onOpen() {
      this.previouslyFocused = document.activeElement
      const body = document.body
      this.savedOverflow = body.style.overflow
      this.savedPaddingRight = body.style.paddingRight
      const scrollbar = window.innerWidth - document.documentElement.clientWidth
      if (scrollbar > 0) body.style.paddingRight = `${scrollbar}px`
      body.style.overflow = 'hidden'
      this.$nextTick(() => {
        const panel = this.$refs.panel
        if (!panel) return
        const first = panel.querySelector(FOCUSABLE)
        // the header's close button is first; prefer the first field of the form body
        const field = panel.querySelector('input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled])')
        ;(field || first || panel).focus()
      })
    },
    onClose() {
      const body = document.body
      body.style.overflow = this.savedOverflow
      body.style.paddingRight = this.savedPaddingRight
      const target = this.previouslyFocused
      this.previouslyFocused = null
      if (target && typeof target.focus === 'function' && document.contains(target)) target.focus()
    },
    onKeydown(event) {
      if (event.key === 'Escape') {
        event.stopPropagation()
        this.close()
        return
      }
      if (event.key !== 'Tab') return
      const items = [...this.$refs.panel.querySelectorAll(FOCUSABLE)]
      if (!items.length) {
        event.preventDefault()
        this.$refs.panel.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (event.shiftKey && (active === first || active === this.$refs.panel)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }
}
</script>
