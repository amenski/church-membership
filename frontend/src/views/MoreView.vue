<template>
  <div>
    <PageHead title="More" />
    <ul class="m-0 list-none overflow-hidden rounded-lg border border-rule bg-paper p-0">
      <li v-for="row in rows" :key="row.to" class="border-b border-rule last:border-b-0">
        <router-link :to="row.to" class="flex min-h-[60px] items-center gap-3.5 px-4 py-2 text-ink no-underline hover:bg-teal-tint">
          <Icon :name="row.icon" :size="20" class="shrink-0 text-muted" />
          <span class="min-w-0 flex-1">
            <span class="block text-lg font-medium">{{ row.label }}</span>
            <span class="block text-sm text-muted">{{ row.subtitle }}</span>
          </span>
          <Icon name="chevron-right" :size="20" class="shrink-0 text-muted" />
        </router-link>
      </li>
    </ul>

    <section aria-label="Signed in" class="mt-4 flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
      <div>
        <div class="truncate text-lg font-semibold" :title="displayName">{{ displayName }}</div>
        <div v-if="email && email !== displayName" class="truncate text-base text-muted" :title="email">{{ email }}</div>
      </div>
      <button
        type="button"
        class="inline-flex min-h-12 w-full cursor-pointer items-center justify-center gap-2 rounded-md border border-clay bg-paper text-lg font-medium text-clay hover:bg-clay-tint"
        @click="signOut"
      >
        <Icon name="log-out" :size="18" />Sign out
      </button>
    </section>
  </div>
</template>

<script>
import { useAppStore } from '@/stores/appStore'
import { useAuthStore } from '@/stores/authStore'
import Icon from '@/components/Icon.vue'
import PageHead from '@/components/PageHead.vue'

// The screen behind the tab bar's More: what the bar has no room for. Phone only; from lg the rail has all of it.
const WIDE = '(min-width: 62rem)'

export default {
  name: 'MoreView',
  components: { Icon, PageHead },
  setup() {
    return { authStore: useAuthStore(), appStore: useAppStore() }
  },
  computed: {
    rows() {
      return [
        { to: '/households', label: 'Households', subtitle: 'Families and shared addresses', icon: 'home' },
        ...(this.authStore.hasRole('ADMIN') ? [{ to: '/activity', label: 'Activity', subtitle: 'Administrators only', icon: 'clock' }] : []),
        { to: '/profile', label: 'Profile', subtitle: 'Your details and password', icon: 'user' }
      ]
    },
    displayName() {
      const user = this.authStore.currentUser
      return [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim() || user?.email || ''
    },
    email() {
      return this.authStore.currentUser?.email || ''
    }
  },
  created() {
    this.query = window.matchMedia?.(WIDE)
    if (this.query?.matches) this.leave()
    else this.query?.addEventListener('change', this.onWide)
  },
  beforeUnmount() {
    this.query?.removeEventListener('change', this.onWide)
  },
  methods: {
    // On desktop the rail already lists these: /more goes to the Overview (also when the window grows)
    onWide(event) {
      if (event.matches) this.leave()
    },
    leave() {
      this.$router.replace('/dashboard')
    },
    async signOut() {
      try {
        await this.authStore.logout()
        this.appStore.addNotification({ type: 'success', title: 'Sign out', message: 'You have been successfully signed out', isToast: true })
        this.$router.push('/login')
      } catch {
        this.appStore.addNotification({ type: 'error', title: 'Logout Failed', message: 'An error occurred while signing out', isToast: true })
      }
    }
  }
}
</script>
