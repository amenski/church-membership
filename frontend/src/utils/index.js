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
 *
 * DEPRECATED: Import and use the store directly in your components:
 *
 * import { useAppStore } from '@/stores/appStore'
 * const appStore = useAppStore()
 * appStore.addNotification({ message, type, ...options })
 *
 * @param {string} message - The message to display
 * @param {string} type - The type of toast (success, error, warning, info)
 * @param {Object} options - Additional options for the notification
 */
export const showToast = (message, type = 'info', options = {}) => {
  // Fallback logging for legacy code - use appStore.addNotification() instead
  if (process.env.NODE_ENV === 'development') {
    console.warn('showToast() is deprecated. Use appStore.addNotification() instead.')
  }
  console.log(`[${type.toUpperCase()}] ${message}`)
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
