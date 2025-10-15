// API Configuration
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// Application Constants
export const APP_NAME = 'Member Tracker'

// Date Formats
export const DATE_FORMAT = 'YYYY-MM-DD'
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'
export const DISPLAY_DATE_FORMAT = 'MMM DD, YYYY'

// Toast/Notification Durations (in milliseconds)
export const TOAST_DURATION = 3000

// Status Constants
export const MEMBER_STATUS = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  PENDING: 'PENDING'
}

export const PAYMENT_STATUS = {
  PAID: 'PAID',
  PENDING: 'PENDING',
  OVERDUE: 'OVERDUE'
}
