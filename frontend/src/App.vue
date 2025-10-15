<template>
  <div id="app" :class="themeClass">
    <!-- Bootstrap Navbar -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary sticky-top">
      <div class="container-fluid">
        <a class="navbar-brand" href="#">{{ appTitle }}</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div id="navbarNav" class="collapse navbar-collapse">
          <ul class="navbar-nav">
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
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <div class="container mt-4 mb-5">
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

const appStore = useAppStore()

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

onMounted(() => {
  appStore.initialize()
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
