// Utility functions for the application

/**
 * Format a date using moment.js
 * @param {string|Date} date - The date to format
 * @param {string} format - The format string
 * @returns {string} Formatted date string
 */
export const formatDate = (date, format = 'MMM DD, YYYY') => {
  if (!date) return ''
  const moment = require('moment')
  return moment(date).format(format)
}

/**
 * Debounce function to limit how often a function can fire
 * @param {Function} func - The function to debounce
 * @param {number} wait - The time to wait in milliseconds
 * @returns {Function} Debounced function
 */
export const debounce = (func, wait = 300) => {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

/**
 * Show a Bootstrap toast notification using Vue store
 * @param {string} message - The message to display
 * @param {string} type - The type of toast (success, error, warning, info)
 * @param {Object} options - Additional options for the notification
 */
export const showToast = (message, type = 'info', options = {}) => {
  // This function now uses Vue's Pinia store instead of direct DOM manipulation
  // Import and use the store in your components like this:
  // import { useAppStore } from '@/stores/appStore'
  // const appStore = useAppStore()
  // appStore.addNotification({ message, type, ...options })

  // For backwards compatibility, we'll try to get the store if available
  if (typeof window !== 'undefined' && window.__PINIA__) {
    try {
      // Dynamically import and use the store
      import('@/stores/appStore').then(({ useAppStore }) => {
        const appStore = useAppStore()
        appStore.addNotification({
          message,
          type,
          title: options.title || type.charAt(0).toUpperCase() + type.slice(1),
          duration: options.duration || 5000,
          ...options
        })
      }).catch(() => {
        // Fallback to console if store is not available
        console.log(`[${type.toUpperCase()}] ${message}`)
      })
    } catch (error) {
      console.log(`[${type.toUpperCase()}] ${message}`)
    }
  } else {
    // Fallback for when Pinia is not available
    console.log(`[${type.toUpperCase()}] ${message}`)
  }
}

/**
 * Format currency values
 * @param {number} amount - The amount to format
 * @param {string} currency - The currency code (default: USD)
 * @returns {string} Formatted currency string
 */
export const formatCurrency = (amount, currency = 'USD') => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency
  }).format(amount)
}

/**
 * Validate email format
 * @param {string} email - The email to validate
 * @returns {boolean} Whether the email is valid
 */
export const isValidEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/**
 * Deep clone an object
 * @param {Object} obj - The object to clone
 * @returns {Object} Cloned object
 */
export const deepClone = (obj) => {
  return JSON.parse(JSON.stringify(obj))
}
