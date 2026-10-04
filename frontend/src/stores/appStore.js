import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Application Store
 * Holds the notifications that ToastHost renders.
 */
export const useAppStore = defineStore('app', () => {
  const notifications = ref([])

  function addNotification(notification) {
    const newNotification = {
      id: Date.now(),
      type: notification.type || 'info',
      title: notification.title || '',
      message: notification.message,
      read: false,
      timestamp: new Date().toISOString(),
      duration: notification.duration || 5000,
      action: notification.action,
      isToast: notification.isToast || false
    }

    notifications.value.unshift(newNotification)

    // Auto-remove after duration if specified
    if (newNotification.duration > 0) {
      setTimeout(() => {
        removeNotification(newNotification.id)
      }, newNotification.duration)
    }

    return newNotification.id
  }

  function removeNotification(id) {
    const index = notifications.value.findIndex(n => n.id === id)
    if (index !== -1) {
      notifications.value.splice(index, 1)
    }
  }

  return {
    notifications,
    addNotification,
    removeNotification
  }
})
