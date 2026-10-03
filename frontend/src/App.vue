<template>
  <div id="app" :class="themeClass">
    <!-- Signed in: slim top bar below lg, left rail from lg up -->
    <template v-if="isAuthenticated">
      <header class="topbar d-lg-none">
        <router-link :to="homePath" class="topbar__brand" aria-label="Felege Selam home">
          <span class="wordmark__geez">ፈለገ ሰላም</span>
          <span class="wordmark__latin">Felege Selam</span>
        </router-link>
        <button
          class="btn btn-secondary topbar__menu"
          type="button"
          data-bs-toggle="offcanvas"
          data-bs-target="#appRail"
          aria-controls="appRail"
          aria-label="Open menu"
        >
          <i class="bi bi-list" aria-hidden="true"></i>
          Menu
        </button>
      </header>

      <div class="rail-frame">
        <aside id="appRail" ref="rail" class="rail offcanvas-lg offcanvas-start" tabindex="-1" aria-label="Main navigation">
          <WovenBand :height="8" />
          <div class="rail__header">
            <router-link :to="homePath" class="wordmark" aria-label="Felege Selam home">
              <span class="wordmark__geez">ፈለገ ሰላም</span>
              <span class="wordmark__latin">Felege Selam</span>
            </router-link>
            <button
              type="button"
              class="btn-close d-lg-none"
              data-bs-dismiss="offcanvas"
              data-bs-target="#appRail"
              aria-label="Close menu"
            ></button>
          </div>

          <nav class="rail__nav" aria-label="Sections">
            <router-link v-if="authStore.hasRole('VOLUNTEER')" to="/dashboard" class="rail__link" active-class="active">
              <i class="bi bi-house-door" aria-hidden="true"></i>Overview
            </router-link>
            <router-link v-if="authStore.hasRole('VOLUNTEER')" to="/members" class="rail__link" active-class="active">
              <i class="bi bi-people" aria-hidden="true"></i>Members
            </router-link>
            <router-link v-if="authStore.hasRole('VOLUNTEER')" to="/payments" class="rail__link" active-class="active">
              <i class="bi bi-cash-coin" aria-hidden="true"></i>Payments
            </router-link>
            <router-link v-if="authStore.hasRole('VOLUNTEER')" to="/communications" class="rail__link" active-class="active">
              <i class="bi bi-chat-left-text" aria-hidden="true"></i>Messages
            </router-link>
            <router-link to="/profile" class="rail__link" active-class="active">
              <i class="bi bi-person" aria-hidden="true"></i>Profile
            </router-link>
          </nav>

          <div class="rail__user">
            <div class="rail__user-name">{{ displayName }}</div>
            <div v-if="displayName !== currentUser?.email && currentUser?.email" class="rail__user-email">{{ currentUser.email }}</div>
            <button type="button" class="btn btn-secondary btn-sm mt-3" @click="handleLogout">
              <i class="bi bi-box-arrow-right" aria-hidden="true"></i>Sign out
            </button>
          </div>
        </aside>
      </div>
    </template>

    <!-- Main Content -->
    <main :class="isAuthenticated ? 'shell-main' : ''">
      <router-view/>
    </main>

    <!-- Toast Container for Notifications -->
    <div class="toast-container position-fixed bottom-0 end-0 p-3">
      <div
          v-for="notification in notifications"
          :key="notification.id"
          :id="'toast-' + notification.id"
          class="toast"
          :class="notificationClass(notification)"
          role="alert"
          aria-live="assertive"
          aria-atomic="true"
          @hidden.bs.toast="removeNotification(notification.id)"
      >
        <div class="toast-header">
          <strong class="me-auto">{{ notification.title || 'Notification' }}</strong>
          <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body">
          {{ notification.message }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch, nextTick, ref } from 'vue'
import WovenBand from '@/components/WovenBand.vue'
import { useI18n } from 'vue-i18n'
import { Toast, Offcanvas } from 'bootstrap'
import { useAppStore } from '@/stores/appStore'
import { useAuthStore } from '@/stores/authStore'
import { useRouter, useRoute } from 'vue-router'

const { t } = useI18n()
const appStore = useAppStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const rail = ref(null)
const toastElements = ref({})

const themeClass = computed(() => {
  const theme = appStore.currentTheme
  if (theme === 'dark') return 'data-bs-theme="dark"'
  if (theme === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'data-bs-theme="dark"' : 'data-bs-theme="light"'
  }
  return 'data-bs-theme="light"'
})

const notifications = computed(() => appStore.notifications)
const isAuthenticated = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)
const displayName = computed(() => {
  const user = currentUser.value
  const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
  return name || user?.email || ''
})
const homePath = computed(() => (authStore.hasRole('VOLUNTEER') ? '/dashboard' : '/profile'))

// Close the mobile menu after the user picks a page
watch(() => route.fullPath, () => {
  if (rail.value) Offcanvas.getInstance(rail.value)?.hide()
})

const notificationClass = (notification) => {
  return `toast-note toast-note--${notification.type || 'info'}`
}

const removeNotification = (id) => {
  appStore.removeNotification(id)
}

// Watch for new notifications and show toast
watch(notifications, async (newNotifications, oldNotifications) => {
  if (newNotifications.length > oldNotifications.length) {
    // A new notification was added
    const newNotification = newNotifications[0]
    await nextTick()
    // Find and show the toast element
    const toastElement = document.getElementById(`toast-${newNotification.id}`)
    if (toastElement) {
      const toast = new Toast(toastElement, {
        autohide: newNotification.duration > 0,
        delay: newNotification.duration || 5000
      })
      toast.show()
    }
  }
}, { deep: true })

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

<style>
.toast-container {
  z-index: 1100;
}

#app {
  min-height: 100vh;
}

/* ---------- Wordmark ---------- */
.wordmark,
.topbar__brand {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  color: var(--felege-ink);
}
.wordmark__geez {
  font-family: var(--felege-font-geez);
  font-weight: 700;
  font-size: 1.375rem;
  line-height: 1.3;
  color: var(--felege-teal);
}
.wordmark__latin {
  font-family: var(--felege-font-display);
  font-weight: 700;
  font-size: 1.125rem;
  line-height: 1.2;
  color: var(--felege-ink);
}

/* ---------- Top bar (below lg) ---------- */
.topbar {
  position: sticky;
  top: 0;
  z-index: 1020;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-4);
  background: var(--felege-paper);
  border-bottom: 1px solid var(--felege-rule);
}
.topbar__brand { flex-direction: row; align-items: baseline; gap: var(--space-3); }
.topbar__brand .wordmark__geez { font-size: 1.125rem; }
.topbar__menu i { font-size: 1.25rem; line-height: 1; }

/* ---------- Rail ---------- */
.rail {
  --bs-offcanvas-width: 280px;
  --bs-offcanvas-bg: var(--felege-paper);
  --bs-offcanvas-border-color: var(--felege-rule);
  display: flex;
  flex-direction: column;
  background: var(--felege-paper);
}
.rail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--space-5) var(--space-5) var(--space-4);
}
.rail__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) 0;
  flex: 1 1 auto;
}
.rail__link {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px var(--space-5);
  font-size: 1.125rem;
  font-weight: 500;
  color: var(--felege-ink);
  text-decoration: none;
  transition: color 0.12s;
}
.rail__link i { font-size: 1.125rem; color: var(--felege-muted); transition: color 0.12s; }
.rail__link:hover,
.rail__link:hover i { color: var(--felege-teal); }
.rail__link.active,
.rail__link.active i { color: var(--felege-teal); }
.rail__link.active { font-weight: 700; }
.rail__link.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: var(--felege-teal);
}
.rail__user {
  padding: var(--space-4) var(--space-5) var(--space-5);
  border-top: 1px solid var(--felege-rule);
}
.rail__user-name { font-size: 1rem; font-weight: 700; overflow-wrap: anywhere; }
.rail__user-email { font-size: 0.875rem; color: var(--felege-muted); overflow-wrap: anywhere; }

@media (min-width: 992px) {
  .rail-frame {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: 248px;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--felege-rule);
    background: var(--felege-paper);
    overflow-y: auto;
  }
  .rail { min-height: 100%; }
  .shell-main { margin-left: 248px; }
}

/* ---------- Content area ---------- */
.shell-main > * {
  max-width: 1100px;
  margin-left: auto;
  margin-right: auto;
  padding: var(--space-6);
}
@media (max-width: 575.98px) {
  .shell-main > * { padding: var(--space-5) var(--space-4); }
}
</style>
