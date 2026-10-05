// Utility functions for the application
import { format as dateFnsFormat } from 'date-fns'
import { enUS } from 'date-fns/locale'
import { am } from '@/utils/dateLocale'
import i18n from '@/i18n'

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

// Month and weekday names follow the UI language; the calendar stays Gregorian either way
const dateLocale = () => (i18n.global.locale.value === 'am' ? am : enUS)

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
  return dateFnsFormat(toDate(date), fmt, { locale: dateLocale() })
}

/**
 * Today (or the given date) as a local YYYY-MM-DD string, for date inputs.
 * toISOString() would give the UTC day, which is yesterday late in the evening west of UTC.
 */
export const localISODate = (date = new Date()) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

/**
 * Money as shown on the Overview and Payments: "$1,520.00". Missing or non-numeric is $0.00.
 * @param {number|string} amount
 * @returns {string}
 */
export const formatMoney = (amount) => money.format(Number(amount) || 0)

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
