import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * Application Store
 * Manages global application state, theme, user preferences, etc.
 */
export const useAppStore = defineStore('app', () => {
  // State
  const isLoading = ref(false)
  const loadingMessage = ref('')
  const currentTheme = ref('light')
  const language = ref('en')
  const sidebarCollapsed = ref(false)
  const notifications = ref([])
  const userPreferences = ref({
    itemsPerPage: 10,
    defaultView: 'dashboard',
    enableNotifications: true,
    autoRefresh: false,
    refreshInterval: 30000 // 30 seconds
  })

  // Getters
  const appVersion = computed(() => process.env.VUE_APP_APP_VERSION || '1.0.0')
  const appTitle = computed(() => process.env.VUE_APP_APP_TITLE || 'Member Tracker')
  const isDevelopment = computed(() => process.env.NODE_ENV === 'development')
  const isProduction = computed(() => process.env.NODE_ENV === 'production')

  const loadingProgress = computed(() => {
    if (!isLoading.value) return 0
    return 50 // Simulate progress, in real app this would be dynamic
  })

  const unreadNotifications = computed(() =>
    notifications.value.filter(notification => !notification.read)
  )

  // Actions
  function setLoading(loading, message = '') {
    isLoading.value = loading
    loadingMessage.value = message
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setTheme(theme) {
    if (!['light', 'dark', 'auto'].includes(theme)) {
      console.warn(`Invalid theme: ${theme}`)
      return
    }

    currentTheme.value = theme
  }

  function setLanguage(lang) {
    if (!['en', 'es', 'fr'].includes(lang)) { // Add more languages as needed
      console.warn(`Unsupported language: ${lang}`)
      return
    }

    language.value = lang
  }

  function addNotification(notification) {
    const newNotification = {
      id: Date.now(),
      type: notification.type || 'info',
      title: notification.title || '',
      message: notification.message,
      read: false,
      timestamp: new Date().toISOString(),
      duration: notification.duration || 5000,
      action: notification.action,
      isToast: notification.isToast || false
    }

    notifications.value.unshift(newNotification)

    // Auto-remove after duration if specified
    if (newNotification.duration > 0) {
      setTimeout(() => {
        removeNotification(newNotification.id)
      }, newNotification.duration)
    }

    return newNotification.id
  }

  function removeNotification(id) {
    const index = notifications.value.findIndex(n => n.id === id)
    if (index !== -1) {
      notifications.value.splice(index, 1)
    }
  }

  function markNotificationAsRead(id) {
    const notification = notifications.value.find(n => n.id === id)
    if (notification && !notification.read) {
      notification.read = true
    }
  }

  function clearAllNotifications() {
    notifications.value = []
  }

  function updateUserPreferences(newPreferences) {
    userPreferences.value = {
      ...userPreferences.value,
      ...newPreferences
    }

    // Save to localStorage
    savePreferencesToStorage()
  }

  function savePreferencesToStorage() {
    try {
      localStorage.setItem('memberTracker_preferences', JSON.stringify(userPreferences.value))
    } catch (error) {
      console.warn('Failed to save preferences to localStorage', error)
    }
  }

  function loadPreferencesFromStorage() {
    try {
      const saved = localStorage.getItem('memberTracker_preferences')
      if (saved) {
        userPreferences.value = JSON.parse(saved)
      }
    } catch (error) {
      console.warn('Failed to load preferences from localStorage', error)
    }
  }

  function initialize() {
    // Load saved preferences
    loadPreferencesFromStorage()

    // Set up theme change listener for auto theme
    if (currentTheme.value === 'auto') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      const updateTheme = () => setTheme('auto')
      mediaQuery.addEventListener('change', updateTheme)
    }
  }

  function reset() {
    isLoading.value = false
    loadingMessage.value = ''
    sidebarCollapsed.value = false
    notifications.value = []

    // Reset preferences to defaults but keep storage
    userPreferences.value = {
      itemsPerPage: 10,
      defaultView: 'dashboard',
      enableNotifications: true,
      autoRefresh: false,
      refreshInterval: 30000
    }
  }

  // Expose state and actions
  return {
    // State
    isLoading,
    loadingMessage,
    currentTheme,
    language,
    sidebarCollapsed,
    notifications,
    userPreferences,

    // Getters
    appVersion,
    appTitle,
    isDevelopment,
    isProduction,
    loadingProgress,
    unreadNotifications,

    // Actions
    setLoading,
    toggleSidebar,
    setTheme,
    setLanguage,
    addNotification,
    removeNotification,
    markNotificationAsRead,
    clearAllNotifications,
    updateUserPreferences,
    initialize,
    reset
  }
})
