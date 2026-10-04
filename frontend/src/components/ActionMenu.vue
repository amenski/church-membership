<template>
  <span class="inline-flex">
    <button
      ref="trigger"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="open ? menuId : undefined"
      :aria-label="label"
      class="flex size-9 cursor-pointer items-center justify-center rounded-sm border border-transparent bg-transparent text-muted hover:border-field hover:bg-teal-tint hover:text-ink"
      :class="open ? 'border-field bg-teal-tint text-ink' : ''"
      @click="toggle"
      @keydown="onTriggerKeydown"
    >
      <Icon name="more-horizontal" :size="18" />
    </button>

    <Teleport to="body">
      <ul
        v-if="open"
        :id="menuId"
        ref="menu"
        role="menu"
        :aria-label="label"
        class="fixed z-[1100] m-0 min-w-44 list-none rounded-md border border-rule bg-paper p-0 text-ink shadow-modal"
        :style="position"
        @keydown="onMenuKeydown"
      >
        <li v-for="item in items" :key="item.key" role="none" class="border-b border-rule last:border-b-0">
          <button
            type="button"
            role="menuitem"
            tabindex="-1"
            :disabled="item.disabled"
            :class="[
              'flex min-h-11 w-full cursor-pointer items-center border-0 bg-transparent px-4 py-2 text-left font-sans text-base font-medium hover:bg-teal-tint focus:bg-teal-tint focus-visible:outline-offset-[-2px] disabled:cursor-default disabled:text-muted disabled:hover:bg-transparent',
              item.danger ? 'text-clay' : 'text-ink'
            ]"
            @click="choose(item)"
          >
            {{ item.label }}
          </button>
        </li>
      </ul>
    </Teleport>
  </span>
</template>

<script>
import Icon from '@/components/Icon.vue'

let nextId = 0

export default {
  name: 'ActionMenu',
  components: { Icon },
  props: {
    // aria-label of the trigger, e.g. "More actions for Sarah Brown"
    label: { type: String, required: true },
    // [{ key, label, danger?, disabled? }]
    items: { type: Array, required: true }
  },
  emits: ['select'],
  data() {
    return { open: false, position: {}, menuId: `action-menu-${nextId++}` }
  },
  beforeUnmount() {
    this.unlisten()
  },
  methods: {
    // Enter and Space arrive as a click with detail 0: open and focus the first item;
    // a mouse click opens and leaves focus on the trigger
    toggle(event) {
      if (this.open) this.close()
      else this.openMenu(event.detail === 0 ? 0 : null)
    },
    // focusIndex: item to focus once open (-1 is the last), null leaves focus on the trigger
    async openMenu(focusIndex) {
      this.open = true
      this.listen()
      await this.$nextTick()
      this.place()
      if (focusIndex !== null) this.focusItem(focusIndex)
    },
    // Fixed position from the trigger's box: not clipped by an overflow container,
    // right edge aligned to the trigger, flipped above it when there is no room below
    place() {
      const trigger = this.$refs.trigger
      const menu = this.$refs.menu
      if (!trigger || !menu) return
      const box = trigger.getBoundingClientRect()
      const height = menu.offsetHeight
      const width = menu.offsetWidth
      const below = window.innerHeight - box.bottom
      const top = below >= height + 8 || box.top < height + 8 ? box.bottom + 4 : box.top - height - 4
      const left = Math.max(8, Math.min(box.right - width, window.innerWidth - width - 8))
      this.position = { top: `${top}px`, left: `${left}px` }
    },
    close(returnFocus = true) {
      if (!this.open) return
      this.open = false
      this.unlisten()
      if (returnFocus) this.$refs.trigger?.focus()
    },
    choose(item) {
      if (item.disabled) return
      this.close()
      this.$emit('select', item.key)
    },
    enabledItems() {
      return [...(this.$refs.menu?.querySelectorAll('[role="menuitem"]:not([disabled])') || [])]
    },
    focusItem(index) {
      const items = this.enabledItems()
      if (!items.length) return
      items[(index + items.length) % items.length].focus()
    },
    onTriggerKeydown(event) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        const index = event.key === 'ArrowDown' ? 0 : -1
        if (this.open) this.focusItem(index)
        else this.openMenu(index)
      }
      if (event.key === 'Escape' && this.open) {
        event.stopPropagation()
        this.close()
      }
    },
    onMenuKeydown(event) {
      const items = this.enabledItems()
      const current = items.indexOf(document.activeElement)
      switch (event.key) {
        case 'ArrowDown':
          event.preventDefault()
          this.focusItem(current + 1)
          break
        case 'ArrowUp':
          event.preventDefault()
          this.focusItem(current - 1)
          break
        case 'Home':
          event.preventDefault()
          this.focusItem(0)
          break
        case 'End':
          event.preventDefault()
          this.focusItem(-1)
          break
        case 'Escape':
          // keep a surrounding dialog or page from also reacting
          event.stopPropagation()
          event.preventDefault()
          this.close()
          break
        case 'Tab':
          // focus goes back to the trigger first, so Tab continues from the row, not from the end of the page
          this.close()
          break
      }
    },
    onOutsidePress(event) {
      if (this.$refs.menu?.contains(event.target) || this.$refs.trigger?.contains(event.target)) return
      this.close(false)
    },
    // Phones fire resize and scroll on their own (address bar, momentum): follow the trigger, do not close
    onViewportChange() {
      this.place()
    },
    listen() {
      document.addEventListener('mousedown', this.onOutsidePress)
      window.addEventListener('resize', this.onViewportChange)
      window.addEventListener('scroll', this.onViewportChange, true)
    },
    unlisten() {
      document.removeEventListener('mousedown', this.onOutsidePress)
      window.removeEventListener('resize', this.onViewportChange)
      window.removeEventListener('scroll', this.onViewportChange, true)
    }
  }
}
</script>
