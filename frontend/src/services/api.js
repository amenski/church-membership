import axios from 'axios'
import { useAuthStore, useAppStore } from '@/stores/index.js'

// Configuration from environment variables
const API_CONFIG = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: parseInt(import.meta.env.VITE_API_TIMEOUT || '30000') || 30000,
  retryAttempts: parseInt(import.meta.env.VITE_API_RETRY_ATTEMPTS || '3') || 3,
  retryDelay: 1000 // 1 second
}

// Create axios instance with default configuration
const api = axios.create({
  baseURL: API_CONFIG.baseURL,
  timeout: API_CONFIG.timeout,
  withCredentials: true, // Include cookies in requests
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
})

// Helper function to get CSRF token from cookies
function getCsrfToken() {
  if (typeof document === 'undefined') return null

  const cookies = document.cookie.split(';')
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split('=')
    if (name === 'XSRF-TOKEN') {
      return decodeURIComponent(value)
    }
  }
  return null
}

// Request interceptor
api.interceptors.request.use(
  (config) => {
    // Add timestamp to avoid caching issues
    if (config.method === 'get') {
      config.params = {
        ...config.params,
        _t: Date.now()
      }
    }

    // Add CSRF token for non-GET requests
    if (config.method && ['post', 'put', 'patch', 'delete'].includes(config.method.toLowerCase())) {
      const csrfToken = getCsrfToken()
      if (csrfToken) {
        config.headers['X-XSRF-TOKEN'] = csrfToken
      }
    }

    // Update activity timestamp on each request
    try {
      const authStore = useAuthStore()
      if (authStore && authStore.updateActivity) {
        authStore.updateActivity()
      }
    } catch (error) {
      // Store may not be available during initialization
    }

    // Log request in development
    if (import.meta.env.DEV) {
      console.debug(`API Request: ${config.method?.toUpperCase()} ${config.url}`, config.data)
    }

    return config
  },
  (error) => {
    console.error('API Request Interceptor Error', error)
    return Promise.reject(error)
  }
)

// Surface the server's RFC 7807 ProblemDetail on the error: `message` becomes
// the server's `detail`, `fieldErrors` the list of {field, message}.
// Network errors (no response) are left alone.
function applyProblemDetail(error) {
  const data = error.response?.data
  if (error.response && data) {
    error.message = data.detail || error.message
    error.fieldErrors = data.errors || []
  }
  return error
}

// Response interceptor
api.interceptors.response.use(
  (response) => {
    // Log successful response in development
    if (import.meta.env.DEV) {
      console.debug(`API Response: ${response.status} ${response.config.url}`)
    }

    return response
  },
  async (error) => {
    const originalRequest = error.config

    // Handle 401 Unauthorized (token expired) - attempt refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      // Don't try to refresh if we're already on the login or refresh endpoint
      if (originalRequest.url?.includes('/login') || originalRequest.url?.includes('/refresh')) {
        return Promise.reject(applyProblemDetail(error))
      }

      try {
        // Attempt to refresh tokens
        await apiService.refreshToken()
        // Update activity timestamp after successful refresh
        try {
          const authStore = useAuthStore()
          if (authStore && authStore.updateActivity) {
            authStore.updateActivity()
          }
        } catch (e) {
          // Store may not be available
        }
        // Retry the original request with new tokens
        return api(originalRequest)
      } catch (refreshError) {
        // Refresh failed, redirect to login
        console.error('Token refresh failed:', refreshError)

        // Clear authentication state (remember whether the user was signed in)
        let wasAuthenticated = false
        try {
          const authStore = useAuthStore()
          wasAuthenticated = !!authStore?.isAuthenticated
          if (authStore && authStore.clearAuth) {
            authStore.clearAuth()
          }
        } catch (e) {
          // Store may not be available
        }

        // Clear any stored authentication state
        localStorage.removeItem('user')
        sessionStorage.removeItem('user')
        sessionStorage.removeItem('auth_timestamp')

        // Hard-redirect only a user who had a session to expire; for a
        // logged-out visitor the router guard decides where to go
        if (wasAuthenticated && typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.href = '/login?session=expired'
        }
        return Promise.reject(refreshError)
      }
    }

    // Handle 401 Unauthorized (access denied - not authenticated)
    if (error.response?.status === 401) {
      console.error('Unauthorized access:', originalRequest?.url)

      // Show notification to user
      try {
        const appStore = useAppStore()
        if (appStore && appStore.addNotification) {
          const errorData = error.response?.data
          const errorMessage = errorData?.detail || errorData?.title || 'Authentication required'
          appStore.addNotification({
            type: 'error',
            title: 'Unauthorized',
            message: errorMessage,
            duration: 5000
          })
        }
      } catch (e) {
        // Store may not be available
      }

      return Promise.reject(applyProblemDetail(error))
    }

    // Handle 403 Forbidden (access denied)
    if (error.response?.status === 403) {
      console.error('Access forbidden:', originalRequest?.url)

      // Log security event
      try {
        const authStore = useAuthStore()
        if (authStore && authStore.isLoggedIn) {
          console.warn('User does not have permission for this resource')
        }
      } catch (e) {
        // Store may not be available
      }

      // Show notification to user
      try {
        const appStore = useAppStore()
        if (appStore && appStore.addNotification) {
          // Extract message from Problem Details format (RFC 7807) or fallback
          const errorData = error.response?.data
          const errorMessage = errorData?.detail || errorData?.title || 'You do not have permission to access this resource'
          appStore.addNotification({
            type: 'error',
            title: 'Access Denied',
            message: errorMessage,
            duration: 5000
          })
        }
      } catch (e) {
        // Store may not be available
      }

      // Don't retry 403 errors
      return Promise.reject(applyProblemDetail(error))
    }

    // Check if we should retry the request (non-401/403 errors)
    if (shouldRetryRequest(error) && !originalRequest._retry) {
      originalRequest._retry = true
      originalRequest._retryCount = originalRequest._retryCount || 0

      if (originalRequest._retryCount < API_CONFIG.retryAttempts) {
        originalRequest._retryCount++

        console.warn(`API Retry attempt ${originalRequest._retryCount} for ${originalRequest.url}`)

        // Wait before retrying
        await new Promise(resolve =>
          setTimeout(resolve, API_CONFIG.retryDelay * originalRequest._retryCount)
        )

        return api(originalRequest)
      }
    }

    // Log error details
    console.error('API Error:', {
      message: error.message,
      endpoint: originalRequest?.url,
      method: originalRequest?.method,
      status: error.response?.status,
      data: error.response?.data
    })

    return Promise.reject(applyProblemDetail(error))
  }
)

/**
 * Check if a request should be retried
 */
function shouldRetryRequest(error) {
  // Only retry on network errors or 5xx server errors
  if (!error.response) {
    return true // Network error
  }

  const status = error.response.status
  return status >= 500 && status < 600
}

/**
 * API Service with enhanced error handling and TypeScript-ready structure
 */
const apiService = {
  /**
   * Generic request method with enhanced error handling
   */
  async request(config) {
    const response = await api(config)
    return response.data
  },

  /**
   * Generic GET request
   */
  async get(url, params = {}, config = {}) {
    return this.request({
      method: 'get',
      url,
      params,
      ...config
    })
  },

  /**
   * Generic POST request
   */
  async post(url, data = {}, config = {}) {
    return this.request({
      method: 'post',
      url,
      data,
      ...config
    })
  },

  /**
   * Generic PUT request
   */
  async put(url, data = {}, config = {}) {
    return this.request({
      method: 'put',
      url,
      data,
      ...config
    })
  },

  /**
   * Generic DELETE request
   */
  async delete(url, config = {}) {
    return this.request({
      method: 'delete',
      url,
      ...config
    })
  },

  // Members API
  async getMembers(params = {}) {
    return this.get('/members', params)
  },

  async createMember(member) {
    return this.post('/members', member)
  },

  async updateMember(id, member) {
    return this.put(`/members/${id}`, member)
  },

  // DELETE /members/{id} archives the member (hidden from the lists, payments and messages kept)
  async deleteMember(id) {
    return this.delete(`/members/${id}`)
  },

  async getOverdueMembers(months) {
    return this.get(`/members/overdue/${months}`)
  },

  async exportMembers(ids = []) {
    if (ids.length > 0) {
      return this.post('/members/export', { ids }, { responseType: 'blob' })
    }
    return this.get('/members/export', {}, { responseType: 'blob' })
  },

  // Payments API
  async getPayments(params = {}) {
    return this.get('/payments', params)
  },

  async createPayment(payment) {
    return this.post('/payments', payment)
  },

  async exportPayments() {
    return this.get('/payments/export', {}, { responseType: 'blob' })
  },

  // Communications API
  async getCommunications(params = {}) {
    return this.get('/communications', params)
  },

  async getCommunicationDeliveries(id) {
    return this.get(`/communications/${id}/deliveries`)
  },

  async retryDelivery(communicationId, deliveryId) {
    return this.post(`/communications/${communicationId}/deliveries/${deliveryId}/retry`)
  },

  async sendToAllMembers(communication) {
    return this.post('/communications/send-to-all', communication)
  },

  async sendToOverdueMembers(months, communication) {
    return this.post(`/communications/send-to-overdue/${months}`, communication)
  },

  async sendToMember(memberId, communication) {
    return this.post(`/communications/send-to-member/${memberId}`, communication)
  },

  // Activity log API (ADMIN)
  async getActivityLog(limit = 50) {
    return this.get('/activity-log', { limit })
  },

  // Authentication API
  async login(credentials) {
    const response = await api.post('/auth/login', credentials)

    // Store authentication timestamp
    if (typeof window !== 'undefined') {
      sessionStorage.setItem('auth_timestamp', Date.now().toString())
    }

    return response.data
  },

  async logout() {
    const response = await api.post('/auth/logout')
    // Clear any stored authentication state
    localStorage.removeItem('user')
    sessionStorage.removeItem('user')
    sessionStorage.removeItem('auth_timestamp')
    return response.data
  },

  async refreshToken() {
    try {
      const response = await api.post('/auth/refresh')
      // Update authentication timestamp on refresh
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('auth_timestamp', Date.now().toString())
      }
      return response.data
    } catch (error) {
      console.error('Token refresh failed:', error)
      // Clear authentication state on refresh failure
      localStorage.removeItem('user')
      sessionStorage.removeItem('user')
      sessionStorage.removeItem('auth_timestamp')
      throw error
    }
  },

  async getCurrentUser() {
    const response = await api.get('/users/me')
    // Update activity timestamp on successful user data fetch
    try {
      const authStore = useAuthStore()
      if (authStore && authStore.updateActivity) {
        authStore.updateActivity()
      }
    } catch (error) {
      // Store may not be available
    }
    return response.data
  },

  async updateProfile(profileData) {
    const response = await api.put('/users/me/profile', profileData)
    return response.data
  },

  async changePassword(passwordData) {
    const response = await api.put('/users/me/password', passwordData)
    return response.data
  },

  // Dashboard API
  async getDashboardStats() {
    return this.get('/dashboard/stats')
  },

  async getRecentPayments() {
    return this.get('/dashboard/recent-payments')
  },

  async getOverdueMembers() {
    return this.get('/dashboard/overdue-members')
  },

  async getRecentActivities() {
    return this.get('/dashboard/recent-activities')
  }
}

// Export axios instance for advanced usage
export { api as axiosInstance }

// Export the enhanced API service
export default apiService
