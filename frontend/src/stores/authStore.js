import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiService from '../services/api'

/**
 * Authentication Store
 * Manages user authentication state, login, logout, and registration
 */
export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref(null)
  const isAuthenticated = ref(false)
  const isLoading = ref(false)
  const error = ref(null)
  const authChecked = ref(false)
  const isInitializing = ref(false)
  const lastActivity = ref(null)
  const sessionTimeout = ref(60 * 60 * 1000) // 1 hour in milliseconds
  const refreshInterval = ref(null)

  // Getters
  const currentUser = computed(() => user.value)
  const isLoggedIn = computed(() => isAuthenticated.value)
  const authError = computed(() => error.value)
  const isAuthLoading = computed(() => isLoading.value)
  const hasAuthChecked = computed(() => authChecked.value)

  const userRole = computed(() => user.value?.role || null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isUser = computed(() => user.value?.role === 'USER')
  const sessionExpired = computed(() => {
    if (!lastActivity.value || !isAuthenticated.value) return false
    return Date.now() - lastActivity.value > sessionTimeout.value
  })
  const timeUntilExpiry = computed(() => {
    if (!lastActivity.value || !isAuthenticated.value) return 0
    return Math.max(0, sessionTimeout.value - (Date.now() - lastActivity.value))
  })

  // Helper functions for validation
  function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
  }

  // Actions
  async function login(credentials) {
    try {
      isLoading.value = true
      error.value = null

      // Validate email format
      if (!credentials.email || !isValidEmail(credentials.email)) {
        throw new Error('Please enter a valid email address')
      }

      // Call login API
      const response = await apiService.login({
        email: credentials.email.trim().toLowerCase(),
        password: credentials.password
      })

      // Store user data
      user.value = response.user
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

  async function register(userData) {
    try {
      isLoading.value = true
      error.value = null

      // Validate email
      if (!userData.email || !isValidEmail(userData.email)) {
        throw new Error('Please enter a valid email address')
      }

      // Call registration API
      const response = await apiService.post('/v1/auth/register', {
        email: userData.email.trim().toLowerCase(),
        password: userData.password,
        firstName: userData.firstName?.trim(),
        lastName: userData.lastName?.trim()
      })

      if (import.meta.env.DEV) {
        console.log('User registration successful:', userData.email)
      }

      return response
    } catch (err) {
      error.value = handleAuthError(err)
      if (import.meta.env.DEV) {
        console.warn('User registration failed:', err.message)
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

  async function checkAuth() {
    if (authChecked.value) return isAuthenticated.value ? user.value : null

    try {
      const userData = await apiService.getCurrentUser()

      if (userData) {
        user.value = userData
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

  async function refreshToken() {
    try {
      const response = await apiService.refreshToken()
      // Token refresh is handled by the interceptor, we just need to update user state if needed
      if (response.user) {
        user.value = response.user
      }
      return response
    } catch (err) {
      clearAuth()
      throw err
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
    if (data?.error) {
      return data.error
    }

    switch (status) {
      case 400:
        return data?.message || 'Invalid request. Please check your input.'
      case 401:
        return 'Invalid email or password.'
      case 403:
        return 'Access denied. You do not have permission to perform this action.'
      case 409:
        return 'User already exists with this email.'
      case 422:
        return data?.message || 'Validation failed. Please check your input.'
      case 429:
        return 'Too many login attempts. Please try again later.'
      case 500:
        return 'Server error. Please try again later.'
      default:
        return error.message || 'An unexpected error occurred.'
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
      if (sessionExpired.value) {
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

  function forceLogout() {
    if (import.meta.env.DEV) {
      console.warn('Force logout triggered')
    }
    logout()
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
    isInitializing,
    lastActivity,
    sessionTimeout,

    // Getters
    currentUser,
    isLoggedIn,
    authError,
    isAuthLoading,
    hasAuthChecked,
    userRole,
    isAdmin,
    isUser,
    sessionExpired,
    timeUntilExpiry,

    // Actions
    login,
    register,
    logout,
    checkAuth,
    refreshToken,
    clearAuth,
    clearError,
    handleAuthError,
    setSessionTimeout,
    forceLogout,
    updateActivity,
    initialize
  }
})
