import axios from 'axios'

// Configuration from environment variables
const API_CONFIG = {
  baseURL: process.env.VUE_APP_API_BASE_URL || '/api',
  timeout: parseInt(process.env.VUE_APP_API_TIMEOUT) || 30000,
  retryAttempts: parseInt(process.env.VUE_APP_API_RETRY_ATTEMPTS) || 3,
  retryDelay: 1000 // 1 second
}

// Create axios instance with default configuration
const api = axios.create({
  baseURL: API_CONFIG.baseURL,
  timeout: API_CONFIG.timeout,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
})

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

    // Log request in development
    if (process.env.NODE_ENV === 'development') {
      console.debug(`API Request: ${config.method?.toUpperCase()} ${config.url}`, config.data)
    }

    return config
  },
  (error) => {
    console.error('API Request Interceptor Error', error)
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response) => {
    // Log successful response in development
    if (process.env.NODE_ENV === 'development') {
      console.debug(`API Response: ${response.status} ${response.config.url}`)
    }

    return response
  },
  async (error) => {
    const originalRequest = error.config

    // Check if we should retry the request
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

    return Promise.reject(error)
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

  /**
   * Generic PATCH request
   */
  async patch(url, data = {}, config = {}) {
    return this.request({
      method: 'patch',
      url,
      data,
      ...config
    })
  },

  // Members API
  async getMembers(params = {}) {
    return this.get('/members', params)
  },

  async getMemberById(id) {
    return this.get(`/members/${id}`)
  },

  async createMember(member) {
    return this.post('/members', member)
  },

  async updateMember(id, member) {
    return this.put(`/members/${id}`, member)
  },

  async deleteMember(id) {
    return this.delete(`/members/${id}`)
  },

  async getOverdueMembers(months) {
    return this.get(`/members/overdue/${months}`)
  },

  // Payments API
  async getPayments(params = {}) {
    return this.get('/payments', params)
  },

  async getMemberPayments(memberId) {
    return this.get(`/payments/member/${memberId}`)
  },

  async createPayment(payment) {
    return this.post('/payments', payment)
  },

  // Communications API
  async getCommunications(params = {}) {
    return this.get('/communications', params)
  },

  async createCommunication(communication) {
    return this.post('/communications', communication)
  },

  async sendToAllMembers(communication) {
    return this.post('/communications/send-to-all', communication)
  },

  async sendToOverdueMembers(months, communication) {
    return this.post(`/communications/send-to-overdue/${months}`, communication)
  },

  // Utility methods
  async healthCheck() {
    const response = await api.get('/actuator/health')
    return response.data
  },

  async getApiInfo() {
    const response = await api.get('/actuator/info')
    return response.data
  },

  // File upload helper
  async uploadFile(url, file, onProgress = null) {
    const formData = new FormData()
    formData.append('file', file)

    return this.request({
      method: 'post',
      url,
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: onProgress
    })
  },

  // Cancel token utility
  createCancelToken() {
    return axios.CancelToken.source()
  }
}

// Export axios instance for advanced usage
export { api as axiosInstance }

// Export the enhanced API service
export default apiService
