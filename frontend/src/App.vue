<template>
  <div id="app" class="min-h-screen" :data-density="density">
    <!-- Signed in: slim top bar below lg, left rail from lg up (a drawer below lg) -->
    <template v-if="isAuthenticated">
      <header class="sticky top-0 z-[1020] flex items-center justify-between border-b border-rule bg-paper px-4 py-2 lg:hidden">
        <BrandMark :to="homePath" inline />
        <BaseButton
          ref="menuButton"
          variant="secondary"
          class="min-h-11"
          aria-controls="appRail"
          :aria-expanded="railOpen ? 'true' : 'false'"
          aria-label="Open menu"
          @click="openRail"
        >
          <i class="bi bi-list text-[1.25rem] leading-none" aria-hidden="true"></i>
          Menu
        </BaseButton>
      </header>

      <Transition
        enter-active-class="motion-safe:transition-opacity motion-safe:duration-200"
        enter-from-class="opacity-0"
        leave-active-class="motion-safe:transition-opacity motion-safe:duration-200"
        leave-to-class="opacity-0"
      >
        <div v-if="railOpen" class="fixed inset-0 z-[1040] bg-ink/40 lg:hidden" aria-hidden="true" @click="closeRail"></div>
      </Transition>

      <aside
        id="appRail"
        ref="rail"
        aria-label="Main navigation"
        :inert="!isWide && !railOpen"
        :class="[
          'fixed inset-y-0 left-0 z-[1045] flex w-[280px] flex-col overflow-y-auto border-r border-rule bg-paper lg:w-[248px] lg:translate-x-0',
          'motion-safe:transition-transform motion-safe:duration-200',
          railOpen ? 'translate-x-0' : 'max-lg:-translate-x-full'
        ]"
      >
        <WovenBand :height="8" />
        <div class="flex items-start justify-between px-6 pt-6 pb-4">
          <BrandMark :to="homePath" />
          <button
            type="button"
            class="-mt-1 -mr-2 flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-muted hover:text-ink lg:hidden"
            aria-label="Close menu"
            @click="closeRail"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M3 3l10 10M13 3L3 13" />
            </svg>
          </button>
        </div>

        <nav class="flex flex-1 flex-col gap-[2px] py-2" aria-label="Sections">
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/dashboard" icon="bi-house-door">Overview</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/members" icon="bi-people">Members</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/households" icon="bi-house-heart">Households</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/payments" icon="bi-cash-coin">Payments</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/communications" icon="bi-chat-left-text">Messages</RailLink>
          <RailLink v-if="authStore.hasRole('ADMIN')" to="/activity" icon="bi-clock-history">Activity</RailLink>
          <RailLink to="/profile" icon="bi-person">Profile</RailLink>
        </nav>

        <div class="border-t border-rule px-6 pt-4 pb-6">
          <div class="text-base font-bold [overflow-wrap:anywhere]">{{ displayName }}</div>
          <div v-if="displayName !== currentUser?.email && currentUser?.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ currentUser.email }}</div>
          <BaseButton variant="secondary" size="sm" class="mt-3" @click="handleLogout">
            <i class="bi bi-box-arrow-right" aria-hidden="true"></i>Sign out
          </BaseButton>
        </div>
      </aside>
    </template>

    <!-- Main Content: content centred at 1100px, beside the rail from lg up -->
    <main :inert="railOpen ? true : null" :class="isAuthenticated ? 'lg:ml-[248px] [&>*]:mx-auto [&>*]:max-w-[1100px] [&>*]:p-10 max-sm:[&>*]:px-4 max-sm:[&>*]:py-6' : ''">
      <router-view/>
    </main>

    <ToastHost />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, watch, ref } from 'vue'
import WovenBand from '@/components/WovenBand.vue'
import BaseButton from '@/components/BaseButton.vue'
import BrandMark from '@/components/BrandMark.vue'
import RailLink from '@/components/RailLink.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/appStore'
import { useAuthStore } from '@/stores/authStore'
import { useRouter, useRoute } from 'vue-router'

const { t } = useI18n()
const appStore = useAppStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const rail = ref(null)
const menuButton = ref(null)
const railOpen = ref(false)

const isAuthenticated = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)
const displayName = computed(() => {
  const user = currentUser.value
  const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
  return name || user?.email || ''
})
const homePath = computed(() => (authStore.hasRole('VOLUNTEER') ? '/dashboard' : '/profile'))

// Staff get the dense screens; guests and members get the comfortable ones (docs/design.md)
const density = computed(() => (isAuthenticated.value && authStore.hasRole('VOLUNTEER') ? 'dense' : 'comfortable'))

// The rail is a drawer below lg: focus goes to its first link, then back to the menu button
// (a closed drawer is inert: off screen, out of the tab order and the accessibility tree)
const wideScreen = window.matchMedia('(min-width: 62rem)')
const isWide = ref(wideScreen.matches)

const openRail = async () => {
  railOpen.value = true
  await nextTick()
  rail.value?.querySelector('nav a')?.focus()
}

const closeRail = () => {
  if (!railOpen.value) return
  railOpen.value = false
  menuButton.value?.$el.focus()
}

// Tab wraps inside the open drawer instead of leaving into the page behind the overlay
const onKeydown = (event) => {
  if (event.key === 'Escape') closeRail()
  if (event.key !== 'Tab' || !rail.value) return
  const items = [...rail.value.querySelectorAll('a[href], button:not([disabled])')]
  if (!items.length) return
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  if (!rail.value.contains(active)) {
    event.preventDefault()
    ;(event.shiftKey ? last : first).focus()
  } else if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(railOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) document.addEventListener('keydown', onKeydown)
  else document.removeEventListener('keydown', onKeydown)
})

// Close the drawer after the user picks a page
watch(() => route.fullPath, closeRail)

const onWideScreen = (event) => {
  isWide.value = event.matches
  if (event.matches) closeRail()
}
wideScreen.addEventListener('change', onWideScreen)
onBeforeUnmount(() => {
  wideScreen.removeEventListener('change', onWideScreen)
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

const handleLogout = async () => {
  try {
    await authStore.logout()
    appStore.addNotification({
      type: 'success',
      title: t('auth.signOut'),
      message: 'You have been successfully signed out',
      isToast: true
    })
    router.push('/login')
  } catch (error) {
    appStore.addNotification({
      type: 'error',
      title: 'Logout Failed',
      message: 'An error occurred while signing out',
      isToast: true
    })
  }
}

onMounted(() => {
  authStore.initialize()
})
</script>
