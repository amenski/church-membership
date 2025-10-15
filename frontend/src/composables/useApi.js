import { ref } from 'vue'
import api from '@/services/api'

/**
 * Composable for making API calls with loading and error states
 * @returns {Object} API utilities
 */
export function useApi() {
  const loading = ref(false)
  const error = ref(null)

  const execute = async (apiCall, options = {}) => {
    loading.value = true
    error.value = null

    try {
      const response = await apiCall()
      if (options.onSuccess) {
        options.onSuccess(response.data)
      }
      return response.data
    } catch (err) {
      error.value = err.response?.data?.message || err.message || 'An error occurred'
      if (options.onError) {
        options.onError(error.value)
      }
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    execute,
    api
  }
}
