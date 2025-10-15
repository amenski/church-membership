import { showToast } from '@/utils'

/**
 * Composable for showing toast notifications
 * @returns {Object} Toast utilities
 */
export function useToast() {
  const success = (message) => {
    showToast(message, 'success')
  }

  const error = (message) => {
    showToast(message, 'error')
  }

  const warning = (message) => {
    showToast(message, 'warning')
  }

  const info = (message) => {
    showToast(message, 'info')
  }

  return {
    success,
    error,
    warning,
    info,
    show: showToast
  }
}
