<template>
  <div id="app" :class="[themeClass, 'tw:min-h-screen']" :data-density="density">
    <!-- Signed in: slim top bar below lg, left rail from lg up (a drawer below lg) -->
    <template v-if="isAuthenticated">
      <header class="tw:sticky tw:top-0 tw:z-[1020] tw:flex tw:items-center tw:justify-between tw:border-b tw:border-rule tw:bg-paper tw:px-4 tw:py-2 tw:lg:hidden">
        <BrandMark :to="homePath" inline />
        <BaseButton
          ref="menuButton"
          variant="secondary"
          aria-controls="appRail"
          :aria-expanded="railOpen ? 'true' : 'false'"
          aria-label="Open menu"
          @click="openRail"
        >
          <i class="bi bi-list tw:text-[1.25rem] tw:leading-none" aria-hidden="true"></i>
          Menu
        </BaseButton>
      </header>

      <Transition
        enter-active-class="tw:motion-safe:transition-opacity tw:motion-safe:duration-200"
        enter-from-class="tw:opacity-0"
        leave-active-class="tw:motion-safe:transition-opacity tw:motion-safe:duration-200"
        leave-to-class="tw:opacity-0"
      >
        <div v-if="railOpen" class="tw:fixed tw:inset-0 tw:z-[1040] tw:bg-ink/40 tw:lg:hidden" aria-hidden="true" @click="closeRail"></div>
      </Transition>

      <aside
        id="appRail"
        ref="rail"
        aria-label="Main navigation"
        :inert="!isWide && !railOpen"
        :class="[
          'tw:fixed tw:inset-y-0 tw:left-0 tw:z-[1045] tw:flex tw:w-[280px] tw:flex-col tw:overflow-y-auto tw:border-r tw:border-rule tw:bg-paper tw:lg:w-[248px] tw:lg:translate-x-0',
          'tw:motion-safe:transition-transform tw:motion-safe:duration-200',
          railOpen ? 'tw:translate-x-0' : 'tw:max-lg:-translate-x-full'
        ]"
      >
        <WovenBand :height="8" />
        <div class="tw:flex tw:items-start tw:justify-between tw:px-6 tw:pt-6 tw:pb-4">
          <BrandMark :to="homePath" />
          <button
            type="button"
            class="tw:-mt-1 tw:-mr-2 tw:flex tw:size-9 tw:shrink-0 tw:cursor-pointer tw:items-center tw:justify-center tw:rounded-md tw:border-0 tw:bg-transparent tw:text-muted tw:hover:text-ink tw:lg:hidden"
            aria-label="Close menu"
            @click="closeRail"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M3 3l10 10M13 3L3 13" />
            </svg>
          </button>
        </div>

        <nav class="tw:flex tw:flex-1 tw:flex-col tw:gap-[2px] tw:py-2" aria-label="Sections">
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/dashboard" icon="bi-house-door">Overview</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/members" icon="bi-people">Members</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/payments" icon="bi-cash-coin">Payments</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/communications" icon="bi-chat-left-text">Messages</RailLink>
          <RailLink to="/profile" icon="bi-person">Profile</RailLink>
        </nav>

        <div class="tw:border-t tw:border-rule tw:px-6 tw:pt-4 tw:pb-6">
          <div class="tw:text-base tw:font-bold tw:[overflow-wrap:anywhere]">{{ displayName }}</div>
          <div v-if="displayName !== currentUser?.email && currentUser?.email" class="tw:text-sm tw:text-muted tw:[overflow-wrap:anywhere]">{{ currentUser.email }}</div>
          <BaseButton variant="secondary" size="sm" class="tw:mt-3" @click="handleLogout">
            <i class="bi bi-box-arrow-right" aria-hidden="true"></i>Sign out
          </BaseButton>
        </div>
      </aside>
    </template>

    <!-- Main Content: content centred at 1100px, beside the rail from lg up -->
    <main :class="isAuthenticated ? 'tw:lg:ml-[248px] tw:[&>*]:mx-auto tw:[&>*]:max-w-[1100px] tw:[&>*]:p-10 tw:max-sm:[&>*]:px-4 tw:max-sm:[&>*]:py-6' : ''">
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

const themeClass = computed(() => {
  const theme = appStore.currentTheme
  if (theme === 'dark') return 'data-bs-theme="dark"'
  if (theme === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'data-bs-theme="dark"' : 'data-bs-theme="light"'
  }
  return 'data-bs-theme="light"'
})

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

const onKeydown = (event) => {
  if (event.key === 'Escape') closeRail()
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
