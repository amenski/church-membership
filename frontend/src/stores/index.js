/**
 * Pinia Store Configuration and Export
 * Centralized store management for Member Tracker application
 */

import { createPinia } from 'pinia'
import { useMemberStore } from './memberStore'
import { usePaymentStore } from './paymentStore'
import { useCommunicationStore } from './communicationStore'
import { useAppStore } from './appStore'
import { useAuthStore } from './authStore'

// Create and export Pinia instance
export const pinia = createPinia()

// Export individual stores
export {
  useMemberStore,
  usePaymentStore,
  useCommunicationStore,
  useAppStore,
  useAuthStore
}

// Export store initialization function
export function initializeStores(app) {
  app.use(pinia)

  // Initialize stores with any required setup
  const appStore = useAppStore()
  const authStore = useAuthStore()

  appStore.initialize()
  authStore.initialize()

  return {
    memberStore: useMemberStore(),
    paymentStore: usePaymentStore(),
    communicationStore: useCommunicationStore(),
    appStore,
    authStore
  }
}

export default pinia
