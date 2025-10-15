/**
 * Store Utilities
 * Common utilities and helpers for Pinia stores
 */

/**
 * Store persistence utility for saving store state to localStorage
 */
export function createPersistPlugin(storeName, keysToPersist = []) {
  return ({ store }) => {
    const storageKey = `store_${storeName}`

    // Load persisted state
    try {
      const persistedState = localStorage.getItem(storageKey)
      if (persistedState) {
        const state = JSON.parse(persistedState)
        keysToPersist.forEach(key => {
          if (key in state) {
            store[key] = state[key]
          }
        })
      }
    } catch (error) {
      console.warn(`Failed to load persisted state for ${storeName}:`, error)
    }

    // Watch for changes and persist
    store.$subscribe((mutation, state) => {
      try {
        const stateToPersist = {}
        keysToPersist.forEach(key => {
          if (key in state) {
            stateToPersist[key] = state[key]
          }
        })
        localStorage.setItem(storageKey, JSON.stringify(stateToPersist))
      } catch (error) {
        console.warn(`Failed to persist state for ${storeName}:`, error)
      }
    })
  }
}

/**
 * Store reset utility for resetting store to initial state
 */
export function createResetPlugin() {
  return ({ store, options }) => {
    const initialState = JSON.parse(JSON.stringify(store.$state))

    store.$reset = () => {
      store.$patch(initialState)
    }
  }
}

/**
 * Store validation utility for validating store state
 */
export function createValidationPlugin(validationRules = {}) {
  return ({ store }) => {
    store.$validate = (key, value) => {
      const rules = validationRules[key]
      if (!rules) return { valid: true }

      const errors = []

      if (rules.required && (!value || value === '')) {
        errors.push('This field is required')
      }

      if (rules.minLength && value && value.length < rules.minLength) {
        errors.push(`Minimum length is ${rules.minLength}`)
      }

      if (rules.maxLength && value && value.length > rules.maxLength) {
        errors.push(`Maximum length is ${rules.maxLength}`)
      }

      if (rules.pattern && value && !rules.pattern.test(value)) {
        errors.push('Invalid format')
      }

      return {
        valid: errors.length === 0,
        errors
      }
    }
  }
}

/**
 * Store caching utility for API responses
 */
export function createCachePlugin(cacheConfig = {}) {
  const cache = new Map()
  const defaultTTL = cacheConfig.defaultTTL || 5 * 60 * 1000 // 5 minutes

  return ({ store }) => {
    store.$cache = {
      set(key, value, ttl = defaultTTL) {
        const expiresAt = Date.now() + ttl
        cache.set(key, { value, expiresAt })
      },

      get(key) {
        const cached = cache.get(key)
        if (!cached) return null

        if (Date.now() > cached.expiresAt) {
          cache.delete(key)
          return null
        }

        return cached.value
      },

      delete(key) {
        cache.delete(key)
      },

      clear() {
        cache.clear()
      },

      has(key) {
        const cached = cache.get(key)
        if (!cached) return false

        if (Date.now() > cached.expiresAt) {
          cache.delete(key)
          return false
        }

        return true
      }
    }
  }
}

/**
 * Store performance monitoring utility
 */
export function createPerformancePlugin() {
  return ({ store }) => {
    const timings = new Map()

    store.$perf = {
      start(name) {
        timings.set(name, {
          start: performance.now(),
          end: null,
          duration: null
        })
      },

      end(name) {
        const timing = timings.get(name)
        if (timing && !timing.end) {
          timing.end = performance.now()
          timing.duration = timing.end - timing.start
        }
      },

      measure(name, fn) {
        this.start(name)
        const result = fn()
        this.end(name)
        return result
      },

      async measureAsync(name, fn) {
        this.start(name)
        const result = await fn()
        this.end(name)
        return result
      },

      getTimings() {
        return Array.from(timings.entries()).map(([name, timing]) => ({
          name,
          ...timing
        }))
      },

      clear() {
        timings.clear()
      }
    }
  }
}

/**
 * Store error handling utility
 */
export function createErrorHandlingPlugin() {
  return ({ store }) => {
    store.$handleError = (error, context = {}) => {
      const handledError = {
        ...error,
        store: store.$id,
        context,
        timestamp: new Date().toISOString()
      }

      console.error(`Store error in ${store.$id}:`, handledError)

      // Emit error event for global error handling
      if (typeof window !== 'undefined' && window.dispatchEvent) {
        window.dispatchEvent(new CustomEvent('store-error', {
          detail: handledError
        }))
      }

      return handledError
    }
  }
}

/**
 * Store synchronization utility for real-time updates
 */
export function createSyncPlugin(syncConfig = {}) {
  return ({ store }) => {
    let syncInterval = null

    store.$sync = {
      start(interval = 30000) { // 30 seconds default
        if (syncInterval) {
          clearInterval(syncInterval)
        }

        syncInterval = setInterval(async () => {
          try {
            await store.loadData?.()
          } catch (error) {
            console.warn(`Auto-sync failed for store ${store.$id}:`, error)
          }
        }, interval)
      },

      stop() {
        if (syncInterval) {
          clearInterval(syncInterval)
          syncInterval = null
        }
      },

      isRunning() {
        return syncInterval !== null
      }
    }

    // Cleanup on store destruction
    store.$onAction(() => {
      return () => {
        if (syncInterval) {
          clearInterval(syncInterval)
        }
      }
    })
  }
}

/**
 * Store composition utilities for common store patterns
 */
export const storeComposables = {
  /**
   * Composable for paginated data
   */
  usePagination(initialPage = 1, initialPageSize = 10) {
    const page = ref(initialPage)
    const pageSize = ref(initialPageSize)
    const total = ref(0)

    const totalPages = computed(() =>
      Math.ceil(total.value / pageSize.value)
    )

    const hasNext = computed(() => page.value < totalPages.value)
    const hasPrevious = computed(() => page.value > 1)

    function nextPage() {
      if (hasNext.value) {
        page.value++
      }
    }

    function previousPage() {
      if (hasPrevious.value) {
        page.value--
      }
    }

    function goToPage(pageNum) {
      if (pageNum >= 1 && pageNum <= totalPages.value) {
        page.value = pageNum
      }
    }

    function setPagination(newPagination) {
      if (newPagination.page) page.value = newPagination.page
      if (newPagination.pageSize) pageSize.value = newPagination.pageSize
      if (newPagination.total) total.value = newPagination.total
    }

    return {
      page,
      pageSize,
      total,
      totalPages,
      hasNext,
      hasPrevious,
      nextPage,
      previousPage,
      goToPage,
      setPagination
    }
  },

  /**
   * Composable for filtering data
   */
  useFilters(initialFilters = {}) {
    const filters = ref(initialFilters)

    function setFilter(key, value) {
      filters.value[key] = value
    }

    function setFilters(newFilters) {
      filters.value = { ...filters.value, ...newFilters }
    }

    function clearFilters() {
      filters.value = { ...initialFilters }
    }

    function resetFilters() {
      filters.value = initialFilters
    }

    return {
      filters,
      setFilter,
      setFilters,
      clearFilters,
      resetFilters
    }
  },

  /**
   * Composable for loading states
   */
  useLoading() {
    const isLoading = ref(false)
    const loadingMessage = ref('')

    function setLoading(loading, message = '') {
      isLoading.value = loading
      loadingMessage.value = message
    }

    return {
      isLoading,
      loadingMessage,
      setLoading
    }
  },

  /**
   * Composable for error handling
   */
  useError() {
    const error = ref(null)

    function setError(err) {
      error.value = err
    }

    function clearError() {
      error.value = null
    }

    return {
      error,
      setError,
      clearError
    }
  }
}

export default {
  createPersistPlugin,
  createResetPlugin,
  createValidationPlugin,
  createCachePlugin,
  createPerformancePlugin,
  createErrorHandlingPlugin,
  createSyncPlugin,
  storeComposables
}
