// Utility functions for the application
import { format as dateFnsFormat } from 'date-fns'

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * A date-only string ("2026-10-01") is a calendar day, not an instant: new Date() would read it
 * as midnight UTC, which is the previous evening in every zone west of UTC. Build it as local.
 */
const toDate = (date) => {
  const match = typeof date === 'string' ? DATE_ONLY.exec(date) : null
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : new Date(date)
}

/**
 * Format a date using date-fns
 * @param {string|Date} date - The date to format (a YYYY-MM-DD string is read as a local day)
 * @param {string} fmt - The format string (date-fns pattern)
 * @returns {string} Formatted date string
 */
export const formatDate = (date, fmt = 'MMM dd, yyyy') => {
  if (!date) return ''
  return dateFnsFormat(toDate(date), fmt)
}

/**
 * Today (or the given date) as a local YYYY-MM-DD string, for date inputs.
 * toISOString() would give the UTC day, which is yesterday late in the evening west of UTC.
 */
export const localISODate = (date = new Date()) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
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
  if (import.meta.env.DEV) {
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

/**
 * Trigger a browser download for in-memory data
 * @param {BlobPart} data - The file contents
 * @param {string} filename - The suggested file name
 * @param {string} type - The MIME type
 */
export const downloadBlob = (data, filename, type = 'text/csv') => {
  const blob = new Blob([data], { type })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}
