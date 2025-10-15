/**
 * Pinia Store Configuration and Export
 * Centralized store management for Member Tracker application
 */

import { createPinia } from 'pinia'
import { useMemberStore } from './memberStore'
import { usePaymentStore } from './paymentStore'
import { useCommunicationStore } from './communicationStore'
import { useAppStore } from './appStore'

// Create and export Pinia instance
export const pinia = createPinia()

// Export individual stores
export {
  useMemberStore,
  usePaymentStore,
  useCommunicationStore,
  useAppStore
}

// Export store initialization function
export function initializeStores(app) {
  app.use(pinia)

  // Initialize stores with any required setup
  const appStore = useAppStore()
  appStore.initialize()

  return {
    memberStore: useMemberStore(),
    paymentStore: usePaymentStore(),
    communicationStore: useCommunicationStore(),
    appStore
  }
}

// Export store utilities
export * from './utils'

export default pinia
