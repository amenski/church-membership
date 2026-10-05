<template>
  <div>
    <label :for="id" class="mb-1 block text-xs font-medium text-muted">{{ label }}</label>
    <div ref="field" class="relative">
      <input
        v-bind="$attrs"
        :id="id"
        ref="input"
        type="text"
        role="combobox"
        aria-autocomplete="list"
        :aria-expanded="open ? 'true' : 'false'"
        :aria-controls="open ? listId : undefined"
        :aria-activedescendant="activeId"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? `${id}-error` : undefined"
        :value="selected ? selected.name : query"
        :readonly="!!selected"
        :disabled="disabled"
        :placeholder="placeholder"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        :class="[
          'block h-11 w-full rounded-sm border bg-paper px-2.5 text-lg text-ink lg:h-(--control-h)',
          'placeholder:text-muted',
          'focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal',
          'disabled:bg-mist disabled:text-muted',
          selected && 'pr-11 lg:pr-9',
          error ? 'border-clay' : 'border-field'
        ]"
        @input="onInput"
        @keydown="onKeydown"
        @click="onClick"
        @blur="close"
      />
      <button
        v-if="selected && !disabled"
        type="button"
        :aria-label="`Clear ${selected.name}`"
        class="absolute top-1/2 right-0 flex size-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-sm border-0 bg-transparent text-muted hover:text-ink focus-visible:outline-offset-[-2px] lg:size-(--control-h)"
        @click="clear"
      >
        <Icon name="x" :size="16" />
      </button>
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-1 mb-0 text-xs text-clay">{{ error }}</p>
    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>

    <!-- Fixed under the input and moved out of the dialog body, which scrolls and would clip a list hanging below it;
         it goes into the dialog itself (not the page body) so aria-modal does not hide it from a screen reader -->
    <Teleport :to="host">
      <div
        v-if="open"
        ref="popup"
        class="fixed z-[1300] overflow-y-auto overscroll-contain rounded-md border border-rule bg-paper text-ink shadow-modal"
        :style="position"
        @mousedown.prevent
      >
        <ul :id="listId" role="listbox" :aria-label="label" class="m-0 list-none p-0">
          <li
            v-for="(member, index) in shown"
            :id="optionId(member)"
            :key="member.id"
            role="option"
            :aria-selected="index === activeIndex ? 'true' : 'false'"
            :class="[
              'flex min-h-11 cursor-pointer items-center justify-between gap-3 border-b border-rule px-3 py-1.5 last:border-b-0 lg:min-h-9',
              index === activeIndex && 'bg-teal-tint'
            ]"
            @mousemove="activeIndex = index"
            @click="choose(member)"
          >
            <span class="min-w-0">
              <span class="block text-lg font-medium [overflow-wrap:anywhere] lg:text-base">{{ member.name }}</span>
              <span v-if="detail(member)" class="block text-sm text-muted [overflow-wrap:anywhere]">{{ detail(member) }}</span>
            </span>
            <StatusBadge v-if="badge(member)" :tone="badge(member).tone" class="shrink-0">{{ badge(member).text }}</StatusBadge>
          </li>
        </ul>
        <p v-if="!results.length" class="m-0 px-3 py-3 text-base text-muted [overflow-wrap:anywhere]">No member matches '{{ query.trim() }}'</p>
        <p v-else-if="moreCount" class="m-0 border-t border-rule px-3 py-2 text-sm text-muted">{{ moreCount }} more, keep typing</p>
      </div>
    </Teleport>
  </div>
</template>

<script>
import Icon from '@/components/Icon.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { duesBadge } from '@/utils/dues'
import { nextIndex } from '@/utils/listNavigation'
import { searchMembers } from '@/utils/memberSearch'

const MAX_SHOWN = 8
let nextId = 0

// Type to find a member by name, email or phone, pick with the arrow keys and Enter (the ARIA 1.2
// combobox with a listbox). v-model is the member's id as a string, '' when none is chosen. Once chosen
// the input shows the name, read-only, with a clear button to change it.
export default {
  name: 'MemberPicker',
  components: { Icon, StatusBadge },
  inheritAttrs: false,
  props: {
    id: { type: String, required: true },
    label: { type: String, required: true },
    modelValue: { type: String, default: '' },
    members: { type: Array, default: () => [] },
    placeholder: { type: String, default: 'Search by name, phone or email' },
    error: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    // memberId -> Set of paid "yyyy-MM" months (paidMonthsByMember); without it a row only says "N months behind"
    paidByMember: { type: Map, default: null },
    // "yyyy-MM", the month "due this month" and "paid up" are read for
    currentMonth: { type: String, default: '' }
  },
  emits: ['update:modelValue'],
  data() {
    return { query: '', open: false, activeIndex: -1, position: {}, host: 'body', listId: `member-picker-list-${nextId++}` }
  },
  computed: {
    selected() {
      return this.modelValue ? this.members.find(member => String(member.id) === this.modelValue) || null : null
    },
    results() {
      return searchMembers(this.members, this.query)
    },
    shown() {
      return this.results.slice(0, MAX_SHOWN)
    },
    moreCount() {
      return this.results.length - this.shown.length
    },
    activeId() {
      const member = this.open ? this.shown[this.activeIndex] : null
      return member ? this.optionId(member) : undefined
    },
    announcement() {
      if (!this.open) return ''
      const n = this.results.length
      return n ? `${n} ${n === 1 ? 'result' : 'results'}; use arrow keys` : `No member matches '${this.query.trim()}'`
    }
  },
  watch: {
    // the list changes height as results come and go
    shown() {
      if (this.open) this.$nextTick(this.place)
    },
    disabled(disabled) {
      if (disabled) this.close()
    }
  },
  beforeUnmount() {
    this.unlisten()
  },
  methods: {
    optionId(member) {
      return `${this.id}-option-${member.id}`
    },
    detail(member) {
      return [member.householdName, member.phone].filter(Boolean).join(' · ')
    },
    badge(member) {
      return duesBadge(member, this.paidByMember, this.currentMonth)
    },
    // opens the list (or keeps it open) with nothing highlighted
    show() {
      this.activeIndex = -1
      if (this.open) return
      this.host = this.$refs.input?.closest('[role="dialog"]') || 'body'
      this.open = true
      this.listen()
      this.$nextTick(this.place)
    },
    close() {
      if (!this.open) return
      this.open = false
      this.unlisten()
    },
    onInput(event) {
      this.query = event.target.value
      this.show()
    },
    onClick() {
      if (!this.selected && !this.disabled) this.show()
    },
    onKeydown(event) {
      if (this.disabled || event.isComposing) return
      if (this.selected) {
        // a chosen member is not text to edit: Backspace and Delete take it back out
        if (event.key === 'Backspace' || event.key === 'Delete') {
          event.preventDefault()
          this.clear()
        }
        return
      }
      switch (event.key) {
        case 'ArrowDown':
        case 'ArrowUp':
          event.preventDefault()
          if (!this.open) this.show()
          this.move(event.key)
          break
        case 'Home':
        case 'End':
          if (!this.open) break
          event.preventDefault()
          this.move(event.key)
          break
        case 'Enter': {
          // with the list open Enter never submits the form: it picks the highlighted row, or the one
          // result there is, and with several results and no highlight it does nothing
          if (!this.open) break
          event.preventDefault()
          const member = this.shown[this.activeIndex] || (this.shown.length === 1 ? this.shown[0] : null)
          if (member) this.choose(member)
          break
        }
        case 'Escape':
          // closes the list only; with it already closed Escape reaches the dialog
          if (!this.open) break
          event.preventDefault()
          event.stopPropagation()
          this.close()
          break
        case 'Tab':
          this.close()
          break
      }
    },
    move(key) {
      this.activeIndex = nextIndex(this.activeIndex, key, this.shown.length)
      this.scrollToActive()
    },
    scrollToActive() {
      this.$nextTick(() => document.getElementById(this.activeId)?.scrollIntoView?.({ block: 'nearest' }))
    },
    choose(member) {
      this.query = ''
      this.close()
      this.$emit('update:modelValue', String(member.id))
    },
    async clear() {
      this.query = ''
      this.$emit('update:modelValue', '')
      await this.$nextTick()
      this.$refs.input?.focus()
      this.show()
    },
    // Fixed position from the input's box: not clipped by the dialog body, as wide as the field,
    // flipped above it when there is more room there, kept inside what the keyboard leaves visible
    place() {
      const field = this.$refs.field
      const popup = this.$refs.popup
      if (!field || !popup) return
      const box = field.getBoundingClientRect()
      const viewport = window.visualViewport
      const top = viewport ? viewport.offsetTop : 0
      const bottom = viewport ? viewport.offsetTop + viewport.height : window.innerHeight
      const below = bottom - box.bottom - 8
      const above = box.top - top - 8
      const natural = popup.scrollHeight + 2
      const flip = below < Math.min(natural, 200) && above > below
      const height = Math.max(0, Math.min(natural, flip ? above : below))
      this.position = {
        left: `${box.left}px`,
        width: `${box.width}px`,
        top: `${flip ? box.top - 4 - height : box.bottom + 4}px`,
        maxHeight: `${height}px`
      }
    },
    // Phones fire resize and scroll on their own (address bar, keyboard): follow the field, do not close
    listen() {
      window.addEventListener('resize', this.place)
      window.addEventListener('scroll', this.place, true)
      window.visualViewport?.addEventListener('resize', this.place)
      window.visualViewport?.addEventListener('scroll', this.place)
    },
    unlisten() {
      window.removeEventListener('resize', this.place)
      window.removeEventListener('scroll', this.place, true)
      window.visualViewport?.removeEventListener('resize', this.place)
      window.visualViewport?.removeEventListener('scroll', this.place)
    }
  }
}
</script>
