import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiService from '../services/api'
import { default as i18n, setLocale } from '../i18n'

// The store runs outside a component, so it translates through the app instance
const t = (key, ...args) => i18n.global.t(key, ...args)

/**
 * Authentication Store
 * Manages user authentication state, login and logout
 */
export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref(null)
  const isAuthenticated = ref(false)
  const isLoading = ref(false)
  const error = ref(null)
  const authChecked = ref(false)
  const lastActivity = ref(null)
  const sessionTimeout = ref(60 * 60 * 1000) // 1 hour in milliseconds
  const refreshInterval = ref(null)

  const ROLE_RANK = { MEMBER: 1, VOLUNTEER: 2, STAFF: 3, ADMIN: 4 }

  // True when the signed-in user's role is minRole or higher
  function hasRole(minRole) {
    if (!isAuthenticated.value) return false
    const userRank = ROLE_RANK[user.value?.role]
    const requiredRank = ROLE_RANK[minRole]
    if (!userRank || !requiredRank) return false
    return userRank >= requiredRank
  }

  // Getters
  const currentUser = computed(() => user.value)
  const isLoggedIn = computed(() => isAuthenticated.value)
  const authError = computed(() => error.value)
  const isAuthLoading = computed(() => isLoading.value)

  const userRole = computed(() => user.value?.role || null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isStaff = computed(() => hasRole('STAFF'))
  const isVolunteer = computed(() => hasRole('VOLUNTEER'))
  const homePath = computed(() => (hasRole('VOLUNTEER') ? '/dashboard' : '/my-dues'))

  // Plain functions, not computeds: they read the clock on every call
  function isSessionExpired() {
    if (!lastActivity.value || !isAuthenticated.value) return false
    return Date.now() - lastActivity.value > sessionTimeout.value
  }
  function getTimeUntilExpiry() {
    if (!lastActivity.value || !isAuthenticated.value) return 0
    return Math.max(0, sessionTimeout.value - (Date.now() - lastActivity.value))
  }

  // Helper functions for validation
  function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
  }

  // The account's language, when it has one, beats whatever this device last used. The login
  // response carries only email and role, so in practice this fires from probeAuth's GET /users/me.
  function applyUserLanguage(user) {
    if (user?.language) setLocale(user.language)
  }

  // Actions
  async function login(credentials) {
    try {
      isLoading.value = true
      error.value = null

      // Validate email format
      if (!credentials.email || !isValidEmail(credentials.email)) {
        throw new Error(t('validation.emailInvalid'))
      }

      // Call login API
      const response = await apiService.login({
        email: credentials.email.trim().toLowerCase(),
        password: credentials.password
      })

      // Store user data
      user.value = response.user
      applyUserLanguage(response.user)
      isAuthenticated.value = true
      lastActivity.value = Date.now()

      // Start session monitoring
      startSessionMonitoring()

      if (import.meta.env.DEV) {
        console.log('User login successful:', credentials.email)
      }

      return response
    } catch (err) {
      error.value = handleAuthError(err)
      if (import.meta.env.DEV) {
        console.warn('User login failed:', err.message)
      }
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function logout() {
    try {
      isLoading.value = true

      // Stop session monitoring
      stopSessionMonitoring()

      // Call logout API
      await apiService.logout()

      if (import.meta.env.DEV) {
        console.log('User logout successful')
      }
    } catch (err) {
      console.error('Logout error:', err)
      // Even if logout API fails, clear local state
    } finally {
      // Clear local state regardless of API success
      clearAuth()
      isLoading.value = false
    }
  }

  /**
   * Save the language choice on the account, so it follows the user to another device.
   * A guest has nowhere to save it: the device preference (setLocale) is the whole story.
   */
  async function changeLanguage(language) {
    if (!isAuthenticated.value || !user.value) return null

    const previous = user.value.language
    user.value = { ...user.value, language }
    try {
      const updated = await apiService.updateLanguage(language)
      if (updated) user.value = { ...user.value, ...updated }
      return updated
    } catch (err) {
      user.value = { ...user.value, language: previous }
      throw err
    }
  }

  // The router guard and App both ask on first load: share one probe
  let pendingCheck = null

  function checkAuth() {
    if (authChecked.value) return Promise.resolve(isAuthenticated.value ? user.value : null)
    if (!pendingCheck) pendingCheck = probeAuth().finally(() => { pendingCheck = null })
    return pendingCheck
  }

  async function probeAuth() {
    try {
      const userData = await apiService.getCurrentUser()

      if (userData) {
        user.value = userData
        applyUserLanguage(userData)
        isAuthenticated.value = true
        lastActivity.value = Date.now()
        startSessionMonitoring()
      } else {
        clearAuth()
      }

      return userData
    } catch (err) {
      clearAuth()
      return null
    } finally {
      authChecked.value = true
    }
  }

  function clearAuth() {
    user.value = null
    isAuthenticated.value = false
    error.value = null
    lastActivity.value = null
    stopSessionMonitoring()
    // Don't clear authChecked as it indicates we've attempted to check auth
  }

  function clearError() {
    error.value = null
  }

  function handleAuthError(error) {
    const status = error.response?.status
    const data = error.response?.data

    // Check for specific error messages from backend
    if (data?.detail) {
      return data.detail
    }

    switch (status) {
      case 400:
        return t('errors.invalidRequest')
      case 401:
        return t('errors.invalidCredentials')
      case 403:
        return t('errors.forbidden')
      case 409:
        return t('errors.userExists')
      case 422:
        return t('errors.validationFailed')
      case 429:
        return t('errors.tooManyAttempts')
      case 500:
        return t('errors.serverError')
      default:
        return error.message || t('errors.genericMessage')
    }
  }

  // Session management
  function updateActivity() {
    if (isAuthenticated.value) {
      lastActivity.value = Date.now()
    }
  }

  function startSessionMonitoring() {
    // Clear any existing interval
    stopSessionMonitoring()

    // Check session every 30 seconds
    refreshInterval.value = setInterval(() => {
      if (isSessionExpired()) {
        if (import.meta.env.DEV) {
          console.warn('Session expired due to inactivity')
        }
        logout()
      }
    }, 30000)
  }

  function stopSessionMonitoring() {
    if (refreshInterval.value) {
      clearInterval(refreshInterval.value)
      refreshInterval.value = null
    }
  }

  function setSessionTimeout(timeoutMs) {
    sessionTimeout.value = timeoutMs
  }

  // Initialize store
  function initialize() {
    // Set up activity listeners
    if (typeof window !== 'undefined') {
      const activityEvents = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart']
      activityEvents.forEach(event => {
        document.addEventListener(event, updateActivity, { passive: true })
      })
    }

    // Check if user is already authenticated on app start
    checkAuth().catch(() => {
      // Silent fail - user is not authenticated
    })
  }

  return {
    // State
    user,
    isAuthenticated,
    isLoading,
    error,
    authChecked,
    lastActivity,
    sessionTimeout,

    // Getters
    currentUser,
    isLoggedIn,
    authError,
    isAuthLoading,
    userRole,
    isAdmin,
    isStaff,
    isVolunteer,
    homePath,

    // Actions
    hasRole,
    isSessionExpired,
    getTimeUntilExpiry,
    login,
    logout,
    changeLanguage,
    checkAuth,
    clearAuth,
    clearError,
    handleAuthError,
    setSessionTimeout,
    updateActivity,
    initialize
  }
})
