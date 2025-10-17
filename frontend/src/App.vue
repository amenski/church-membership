<template>
  <div id="app" :class="themeClass">
    <!-- Bootstrap Navbar - Only show when authenticated -->
    <nav v-if="isAuthenticated" class="navbar navbar-expand-lg navbar-dark bg-primary sticky-top">
      <div class="container-fluid">
        <a class="navbar-brand" href="#">{{ appTitle }}</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div id="navbarNav" class="collapse navbar-collapse">
          <ul class="navbar-nav me-auto">
            <li class="nav-item">
              <router-link to="/" class="nav-link" active-class="active">Dashboard</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/members" class="nav-link" active-class="active">Members</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/payments" class="nav-link" active-class="active">Payments</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/communications" class="nav-link" active-class="active">Communications</router-link>
            </li>
          </ul>

          <!-- User Menu -->
          <ul class="navbar-nav ms-auto">
            <li class="nav-item dropdown">
              <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <i class="bi bi-person-circle me-1"></i>
                {{ currentUser?.firstName }} {{ currentUser?.lastName }}
              </a>
              <ul class="dropdown-menu dropdown-menu-end">
                <li><span class="dropdown-item-text small text-muted">Signed in as {{ currentUser?.username }}</span></li>
                <li><hr class="dropdown-divider"></li>
                <li>
                  <a class="dropdown-item" href="#" @click.prevent="handleLogout">
                    <i class="bi bi-box-arrow-right me-2"></i>Sign Out
                  </a>
                </li>
              </ul>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <div :class="isAuthenticated ? 'container mt-4 mb-5' : ''">
      <router-view/>
    </div>

    <!-- Toast Container for Notifications -->
    <div class="toast-container position-fixed bottom-0 end-0 p-3">
      <div
          v-for="notification in notifications"
          :key="notification.id"
          class="toast"
          :class="notificationClass(notification)"
          role="alert"
          aria-live="assertive"
          aria-atomic="true"
          @hidden="removeNotification(notification.id)"
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
import { computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

const appStore = useAppStore()
const authStore = useAuthStore()
const router = useRouter()

const themeClass = computed(() => {
  const theme = appStore.currentTheme
  if (theme === 'dark') return 'data-bs-theme="dark"'
  if (theme === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'data-bs-theme="dark"' : 'data-bs-theme="light"'
  }
  return 'data-bs-theme="light"'
})

const appTitle = computed(() => appStore.appTitle)
const notifications = computed(() => appStore.notifications)
const isAuthenticated = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)

const notificationClass = (notification) => {
  const baseClass = 'toast align-items-center text-white'
  const typeClass = {
    success: 'bg-success',
    error: 'bg-danger',
    warning: 'bg-warning',
    info: 'bg-info'
  }[notification.type] || 'bg-primary'
  return `${baseClass} ${typeClass}`
}

const removeNotification = (id) => {
  appStore.removeNotification(id)
}

const handleLogout = async () => {
  try {
    await authStore.logout()
    appStore.addNotification({
      type: 'success',
      title: 'Signed Out',
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
  // Stores are already initialized in main.js via initializeStores()
  // No need to initialize again here
})
</script>

<style>
.toast-container {
  z-index: 11;
}

#app {
  min-height: 100vh;
}
</style>
