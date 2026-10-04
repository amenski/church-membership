<template>
  <div id="app" class="min-h-screen" :data-density="density">
    <!-- Signed in: a compact top bar over a fixed rail (below lg the rail becomes a drawer) -->
    <template v-if="isAuthenticated">
      <header class="sticky top-0 z-[1020] flex h-12 items-center justify-between gap-4 border-b border-rule bg-paper px-4">
        <div class="flex min-w-0 items-center gap-3">
          <BaseButton
            ref="menuButton"
            variant="secondary"
            size="sm"
            class="min-h-11 w-11 justify-center px-0 lg:hidden"
            aria-controls="appRail"
            :aria-expanded="railOpen ? 'true' : 'false'"
            aria-label="Open menu"
            @click="openRail"
          >
            <Icon name="menu" :size="20" />
          </BaseButton>
          <BrandMark :to="homePath" inline class="lg:hidden" />
          <nav class="hidden min-w-0 lg:block" aria-label="Breadcrumb">
            <ol class="m-0 flex list-none items-center gap-2 p-0 text-sm text-muted">
              <li class="shrink-0">Felege Selam</li>
              <li aria-hidden="true" class="shrink-0 text-rule">/</li>
              <li class="truncate font-medium text-ink" aria-current="page">{{ pageTitle }}</li>
            </ol>
          </nav>
        </div>
        <div class="flex shrink-0 items-center gap-3">
          <span class="hidden text-sm text-muted sm:inline">{{ displayName }}</span>
          <BaseButton variant="secondary" size="sm" @click="handleLogout">
            <Icon name="log-out" :size="16" class="mr-1.5" />Sign out
          </BaseButton>
        </div>
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
          'fixed inset-y-0 left-0 z-[1045] flex w-[280px] flex-col overflow-y-auto border-r border-rule bg-paper lg:top-12 lg:w-[248px] lg:translate-x-0',
          'motion-safe:transition-transform motion-safe:duration-200',
          railOpen ? 'translate-x-0' : 'max-lg:-translate-x-full'
        ]"
      >
        <div class="flex items-center justify-between p-3 lg:hidden">
          <BrandMark :to="homePath" />
          <button
            type="button"
            class="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-sm border-0 bg-transparent text-muted hover:text-ink"
            aria-label="Close menu"
            @click="closeRail"
          >
            <Icon name="x" :size="16" />
          </button>
        </div>

        <nav class="flex flex-1 flex-col gap-0.5 p-3" aria-label="Sections">
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/dashboard" name="home">Overview</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/members" name="users">Members</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/households" name="home">Households</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/payments" name="banknote">Payments</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/communications" name="message-square">Messages</RailLink>
          <RailLink v-if="authStore.hasRole('ADMIN')" to="/activity" name="clock">Activity</RailLink>
          <RailLink to="/profile" name="user">Profile</RailLink>
        </nav>

        <!-- The account block lives in the top bar from lg up, so it stays in the drawer only -->
        <div class="border-t border-rule p-3 lg:hidden">
          <div class="text-sm font-medium [overflow-wrap:anywhere]">{{ displayName }}</div>
          <div v-if="displayName !== currentUser?.email && currentUser?.email" class="text-xs text-muted [overflow-wrap:anywhere]">{{ currentUser.email }}</div>
        </div>
      </aside>
    </template>

    <!-- Content column, beside the rail from lg up -->
    <main :inert="railOpen ? true : null" :class="isAuthenticated ? 'lg:ml-[248px] [&>*]:mx-auto [&>*]:max-w-[1400px] [&>*]:p-6 max-sm:[&>*]:px-4' : ''">
      <router-view/>
    </main>

    <ToastHost />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, watch, ref } from 'vue'
import BaseButton from '@/components/BaseButton.vue'
import BrandMark from '@/components/BrandMark.vue'
import Icon from '@/components/Icon.vue'
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
// The breadcrumb: the page name each route declares in its meta
const pageTitle = computed(() => route.meta?.title || '')

// Staff get the dense screens; guests and members get the comfortable ones
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
