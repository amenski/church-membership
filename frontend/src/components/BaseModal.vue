<template>
  <Teleport to="body">
    <Transition
      enter-active-class="motion-safe:transition motion-safe:duration-150 motion-safe:ease-out"
      enter-from-class="opacity-0"
      leave-active-class="motion-safe:transition motion-safe:duration-100 motion-safe:ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[1200] flex items-center justify-center bg-ink/40 p-4"
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
            'flex max-h-[calc(100dvh-2rem)] w-full flex-col rounded-md border border-rule bg-paper text-ink shadow-modal',
            WIDTH[size] || WIDTH.md
          ]"
        >
          <div class="flex items-start justify-between gap-4 border-b border-rule px-6 py-4">
            <h2 :id="titleId" class="m-0 text-lg leading-tight font-semibold text-ink">{{ title }}</h2>
            <button
              type="button"
              class="-mr-2 -mt-1 flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-muted hover:text-ink"
              aria-label="Close"
              @click="close"
            >
              <Icon name="x" :size="16" />
            </button>
          </div>
          <div class="overflow-y-auto p-6">
            <slot />
          </div>
          <div v-if="$slots.footer" class="flex flex-wrap justify-end gap-2 border-t border-rule px-6 py-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script>
import Icon from '@/components/Icon.vue'

const WIDTH = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-3xl' }
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
let nextId = 0
// open modals, last is on top: only that one answers the keyboard
const openModals = []

export default {
  name: 'BaseModal',
  components: { Icon },
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
      openModals.push(this)
      // on the document, not the panel: focus can fall to the body (a button that got disabled)
      document.addEventListener('keydown', this.onKeydown)
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
      document.removeEventListener('keydown', this.onKeydown)
      const at = openModals.indexOf(this)
      if (at !== -1) openModals.splice(at, 1)
      const body = document.body
      body.style.overflow = this.savedOverflow
      body.style.paddingRight = this.savedPaddingRight
      const target = this.previouslyFocused
      this.previouslyFocused = null
      if (target && typeof target.focus === 'function' && document.contains(target)) target.focus()
    },
    onKeydown(event) {
      if (openModals[openModals.length - 1] !== this) return
      if (event.key === 'Escape') {
        this.close()
        return
      }
      if (event.key !== 'Tab' || !this.$refs.panel) return
      const items = [...this.$refs.panel.querySelectorAll(FOCUSABLE)]
      if (!items.length) {
        event.preventDefault()
        this.$refs.panel.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (!this.$refs.panel.contains(active)) {
        event.preventDefault()
        ;(event.shiftKey ? last : first).focus()
      } else if (event.shiftKey && (active === first || active === this.$refs.panel)) {
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
