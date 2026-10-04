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
        :class="['fixed inset-0 z-[1200] flex items-center justify-center bg-ink/40 p-4', sheet && 'max-lg:p-0']"
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
            WIDTH[size] || WIDTH.md,
            sheet && 'max-lg:h-dvh max-lg:max-h-none max-lg:max-w-none max-lg:rounded-none max-lg:border-0 max-lg:bg-mist max-lg:shadow-none'
          ]"
        >
          <!-- A sheet below lg: a dark top bar with Back on the left; from lg the usual title and close X -->
          <div :class="['flex items-start justify-between gap-4 border-b border-rule px-4 py-3', sheet && 'max-lg:min-h-14 max-lg:shrink-0 max-lg:items-center max-lg:justify-start max-lg:gap-2 max-lg:border-b-0 max-lg:bg-rail max-lg:px-2 max-lg:py-0 max-lg:pt-[env(safe-area-inset-top)]']">
            <h2 :id="titleId" :class="['m-0 text-lg leading-tight font-semibold text-ink', sheet && 'max-lg:order-2 max-lg:text-xl max-lg:text-paper']">{{ title }}</h2>
            <button
              type="button"
              :class="[
                '-mr-2 -mt-1 flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-muted hover:text-ink',
                sheet && 'max-lg:order-1 max-lg:m-0 max-lg:size-11 max-lg:text-paper max-lg:hover:text-paper max-lg:focus-visible:outline-paper'
              ]"
              aria-label="Close"
              @click="close"
            >
              <Icon v-if="sheet" name="chevron-left" :size="22" class="lg:hidden" />
              <Icon name="x" :size="16" :class="sheet && 'max-lg:hidden'" />
            </button>
          </div>
          <div :class="['overflow-y-auto p-4', sheet && 'max-lg:flex-1']">
            <slot />
          </div>
          <div v-if="$slots.footer" :class="['flex flex-wrap justify-end gap-2 border-t border-rule px-4 py-3', sheet && 'max-lg:shrink-0 max-lg:flex-col-reverse max-lg:flex-nowrap max-lg:bg-paper max-lg:pb-[calc(1rem+env(safe-area-inset-bottom))] max-lg:[&>*]:min-h-12! max-lg:[&>*]:w-full']">
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
    size: { type: String, default: 'md', validator: value => ['sm', 'md', 'lg'].includes(value) },
    // below lg the dialog is a full-screen sheet (back arrow, pinned footer with full-width buttons)
    sheet: { type: Boolean, default: false }
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
