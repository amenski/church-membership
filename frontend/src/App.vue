<template>
  <div id="app" class="min-h-screen" :data-density="density">
    <!-- Signed in: a compact top bar over a fixed rail from lg up; below lg staff get the bottom tab bar instead -->
    <template v-if="isAuthenticated">
      <header class="sticky top-0 z-[1020] flex h-12 items-center justify-between gap-4 border-b border-rule bg-paper px-4">
        <div class="flex min-w-0 items-center gap-3">
          <BrandMark :to="homePath" inline class="lg:hidden" />
          <nav class="hidden min-w-0 lg:block" aria-label="Breadcrumb">
            <ol class="m-0 flex list-none items-center gap-2 p-0 text-sm text-muted">
              <li class="shrink-0">Felege Selam</li>
              <li aria-hidden="true" class="shrink-0 text-rule">/</li>
              <li class="truncate font-medium text-ink" aria-current="page">{{ pageTitle }}</li>
            </ol>
          </nav>
        </div>
        <!-- From lg, staff get today's date and a member search; phones have the tab bar and each page's own search -->
        <div v-if="showTabs" class="hidden shrink-0 items-center gap-4 lg:flex">
          <span class="text-sm text-muted">{{ todayText }}</span>
          <form role="search" class="relative" @submit.prevent="searchMembers">
            <label for="topbar-search" class="sr-only">Search members</label>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" focusable="false" class="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" /></svg>
            <input
              id="topbar-search"
              v-model="searchText"
              type="search"
              placeholder="Search members"
              autocomplete="off"
              enterkeyhint="search"
              class="block h-(--control-h) w-56 rounded-sm border border-field bg-paper pr-2.5 pl-8 text-sm text-ink placeholder:text-muted focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal"
            >
          </form>
        </div>
        <!-- Below lg only a signed-in user without the tab bar (a MEMBER) needs the account here: staff have More -->
        <div v-if="!showTabs" class="flex shrink-0 items-center gap-3 lg:hidden">
          <router-link to="/profile" class="hidden min-h-11 items-center text-sm text-muted sm:inline-flex">{{ displayName }}</router-link>
          <BaseButton variant="secondary" size="sm" @click="handleLogout">
            <Icon name="log-out" :size="16" class="mr-1.5" />Sign out
          </BaseButton>
        </div>
      </header>

      <aside
        id="appRail"
        aria-label="Main navigation"
        class="fixed inset-y-0 left-0 z-[1045] hidden w-[232px] flex-col overflow-y-auto bg-rail text-rail-text lg:top-12 lg:flex"
      >
        <div class="px-3 pt-5 pb-4">
          <router-link
            :to="homePath"
            aria-label="Felege Selam home"
            class="flex flex-col rounded-sm px-2.5 py-1 no-underline focus-visible:outline-paper"
          >
            <span class="font-ethiopic text-2xl leading-[1.25] font-bold text-paper">ፈለገ ሰላም</span>
            <span class="mt-0.5 text-xs text-rail-muted">Felege Selam</span>
          </router-link>
        </div>

        <nav class="flex flex-1 flex-col gap-1 px-3 pb-3" aria-label="Sections">
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/dashboard" name="home">Overview</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/members" name="users">Members</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/households" name="home">Households</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/payments" name="banknote">Payments</RailLink>
          <RailLink v-if="authStore.hasRole('VOLUNTEER')" to="/communications" name="message-square">Messages</RailLink>
          <RailLink v-if="authStore.hasRole('ADMIN')" to="/activity" name="clock">Activity</RailLink>
        </nav>

        <!-- The account: the name opens Profile, Sign out ends the session -->
        <div class="mx-3 border-t border-rail-line px-2.5 pt-3.5 pb-4">
          <router-link
            to="/profile"
            class="block rounded-sm text-base font-medium text-paper no-underline hover:underline focus-visible:outline-paper truncate"
            active-class="underline"
            :title="displayName"
          >{{ displayName }}</router-link>
          <div v-if="displayName !== currentUser?.email && currentUser?.email" class="mt-0.5 truncate text-xs text-rail-muted" :title="currentUser.email">{{ currentUser.email }}</div>
          <button
            type="button"
            class="mt-2.5 inline-flex min-h-8 cursor-pointer items-center gap-1.5 rounded-sm border-0 bg-transparent p-0 text-sm text-rail-text hover:text-paper hover:underline focus-visible:outline-paper"
            @click="handleLogout"
          >
            <Icon name="log-out" :size="16" />Sign out
          </button>
        </div>
      </aside>
    </template>

    <!-- Content column, beside the rail from lg up -->
    <!-- The bottom padding keeps the tab bar off the last row -->
    <main :class="isAuthenticated ? ['lg:ml-[232px] [&>*]:mx-auto [&>*]:max-w-[1400px] [&>*]:p-6 max-sm:[&>*]:px-4', showTabs && 'max-lg:pb-[calc(60px+env(safe-area-inset-bottom))]'] : ''">
      <router-view/>
    </main>

    <BottomTabs v-if="showTabs" />

    <ToastHost />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { format } from 'date-fns'
import BaseButton from '@/components/BaseButton.vue'
import BottomTabs from '@/components/BottomTabs.vue'
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

const isAuthenticated = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)
const displayName = computed(() => {
  const user = currentUser.value
  const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
  return name || user?.email || ''
})
const homePath = computed(() => authStore.homePath)
// The breadcrumb: the page name each route declares in its meta
const pageTitle = computed(() => route.meta?.title || '')

// Staff get the dense screens; guests and members get the comfortable ones
const density = computed(() => (isAuthenticated.value && authStore.hasRole('VOLUNTEER') ? 'dense' : 'comfortable'))

// Staff get the bottom tab bar below lg (the rail takes over from lg)
const showTabs = computed(() => isAuthenticated.value && authStore.hasRole('VOLUNTEER'))

// Today in the top bar: reads the clock again on each page change, so it is right after midnight
const todayText = computed(() => {
  void route.fullPath
  return format(new Date(), 'EEEE, d MMMM yyyy')
})

// The top bar search hands the text to the Members screen, which reads ?search=
const searchText = ref('')
const searchMembers = () => {
  const search = searchText.value.trim()
  router.push({ path: '/members', query: search ? { search } : {} })
  searchText.value = ''
}

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
