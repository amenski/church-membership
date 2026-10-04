<template>
  <!-- Top of the screen on phones (the bottom holds the page's action buttons), bottom from sm up -->
  <div class="pointer-events-none fixed inset-x-0 top-0 z-[1300] flex flex-col gap-3 p-4 sm:top-auto sm:bottom-0 sm:left-auto sm:w-[26rem]">
    <TransitionGroup
      enter-active-class="motion-safe:transition motion-safe:duration-200 motion-safe:ease-out"
      enter-from-class="opacity-0 motion-safe:translate-y-2"
      leave-active-class="motion-safe:transition motion-safe:duration-150 motion-safe:ease-in"
      leave-to-class="opacity-0 motion-safe:translate-y-2"
    >
      <div
        v-for="toast in toasts"
        :key="toast.key"
        data-toast
        :role="toast.type === 'error' ? 'alert' : 'status'"
        :aria-live="toast.type === 'error' ? 'assertive' : 'polite'"
        aria-atomic="true"
        :class="[
          'pointer-events-auto flex items-start gap-3 rounded-md border border-l-4 border-rule bg-paper py-3 pl-4 pr-2 text-base text-ink',
          EDGE[toast.type] || EDGE.info
        ]"
        @mouseenter="setHover(toast.key, true)"
        @mouseleave="setHover(toast.key, false)"
        @focusin="setFocus(toast.key, true)"
        @focusout="setFocus(toast.key, false)"
      >
        <div class="min-w-0 flex-1">
          <p v-if="toast.title" class="m-0 font-bold">{{ toast.title }}</p>
          <p class="m-0 [overflow-wrap:anywhere]">{{ toast.message }}</p>
        </div>
        <button
          type="button"
          class="-mt-1 flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-muted hover:text-ink"
          aria-label="Dismiss notification"
          @click="dismiss(toast.key)"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path d="M3 3l10 10M13 3L3 13" />
          </svg>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, toRaw, watch } from 'vue'
import { useAppStore } from '@/stores/appStore'

const EDGE = {
  success: 'border-l-fern',
  error: 'border-l-clay',
  warning: 'border-l-ochre',
  info: 'border-l-teal'
}

const appStore = useAppStore()

// The host keeps its own copy: the store removes a notification when its duration ends,
// which would not let a hover or focus pause the timer.
const toasts = ref([])
// Notification ids are Date.now(): two in the same millisecond share one, so toasts get
// their own key and the store's objects (not ids) tell which ones were already adopted.
const seen = new WeakSet()
let nextKey = 0
// key -> { remaining, startedAt, timer, hover, focus }
const clocks = new Map()

function startClock(key) {
  const clock = clocks.get(key)
  clock.startedAt = Date.now()
  clock.timer = setTimeout(() => dismiss(key), clock.remaining)
}

function pauseClock(id) {
  const clock = clocks.get(id)
  if (!clock || clock.timer === null) return
  clearTimeout(clock.timer)
  clock.timer = null
  clock.remaining -= Date.now() - clock.startedAt
}

function updatePause(id) {
  const clock = clocks.get(id)
  if (!clock) return
  if (clock.hover || clock.focus) pauseClock(id)
  else if (clock.timer === null) startClock(id)
}

function setHover(id, value) {
  const clock = clocks.get(id)
  if (!clock) return
  clock.hover = value
  updatePause(id)
}

function setFocus(id, value) {
  const clock = clocks.get(id)
  if (!clock) return
  clock.focus = value
  updatePause(id)
}

function dismiss(key) {
  const clock = clocks.get(key)
  if (clock && clock.timer !== null) clearTimeout(clock.timer)
  clocks.delete(key)
  const toast = toasts.value.find(item => item.key === key)
  toasts.value = toasts.value.filter(item => item.key !== key)
  if (toast) appStore.removeNotification(toast.id)
}

function adopt(notification) {
  seen.add(toRaw(notification))
  const key = nextKey++
  toasts.value.unshift({ ...notification, key })
  const duration = notification.duration > 0 ? notification.duration : 5000
  clocks.set(key, { remaining: duration, startedAt: 0, timer: null, hover: false, focus: false })
  startClock(key)
}

watch(
  () => appStore.notifications,
  (list) => {
    // oldest first, so the newest ends up on top of the list
    for (const notification of [...list].reverse()) {
      if (!seen.has(toRaw(notification))) adopt(notification)
    }
  },
  { deep: true, immediate: true }
)

onBeforeUnmount(() => {
  clocks.forEach(clock => clock.timer !== null && clearTimeout(clock.timer))
  clocks.clear()
})
</script>
