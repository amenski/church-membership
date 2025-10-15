import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiService from '@/services/api'

/**
 * Communication Store
 * Manages communication data, CRUD operations, and communication-related state
 */
export const useCommunicationStore = defineStore('communications', () => {
  // State
  const communications = ref([])
  const currentCommunication = ref(null)
  const isLoading = ref(false)
  const error = ref(null)
  const filters = ref({
    type: 'all', // 'all', 'email', 'sms', 'notification'
    status: 'all', // 'all', 'draft', 'sent', 'failed'
    dateRange: {
      start: null,
      end: null
    }
  })
  const pagination = ref({
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 0
  })

  // Getters
  const sentCommunications = computed(() =>
    communications.value.filter(comm => comm.status === 'SENT')
  )

  const draftCommunications = computed(() =>
    communications.value.filter(comm => comm.status === 'DRAFT')
  )

  const failedCommunications = computed(() =>
    communications.value.filter(comm => comm.status === 'FAILED')
  )

  const communicationsByType = computed(() => {
    const types = {}
    communications.value.forEach(comm => {
      const type = comm.type
      types[type] = (types[type] || 0) + 1
    })
    return types
  })

  const totalRecipients = computed(() =>
    communications.value.reduce((sum, comm) => sum + comm.recipientCount, 0)
  )

  const recentCommunications = computed(() =>
    communications.value
      .slice()
      .sort((a, b) => new Date(b.sentDate || b.createdDate) - new Date(a.sentDate || a.createdDate))
      .slice(0, 10)
  )

  const filteredCommunications = computed(() => {
    let filtered = communications.value

    // Filter by type
    if (filters.value.type !== 'all') {
      filtered = filtered.filter(comm =>
        comm.type === filters.value.type.toUpperCase()
      )
    }

    // Filter by status
    if (filters.value.status !== 'all') {
      filtered = filtered.filter(comm =>
        comm.status === filters.value.status.toUpperCase()
      )
    }

    // Filter by date range
    if (filters.value.dateRange.start && filters.value.dateRange.end) {
      const startDate = new Date(filters.value.dateRange.start)
      const endDate = new Date(filters.value.dateRange.end)

      filtered = filtered.filter(comm => {
        const commDate = new Date(comm.sentDate || comm.createdDate)
        return commDate >= startDate && commDate <= endDate
      })
    }

    return filtered
  })

  const paginatedCommunications = computed(() => {
    const startIndex = (pagination.value.page - 1) * pagination.value.pageSize
    const endIndex = startIndex + pagination.value.pageSize
    return filteredCommunications.value.slice(startIndex, endIndex)
  })

  const communicationStats = computed(() => ({
    total: communications.value.length,
    sent: sentCommunications.value.length,
    draft: draftCommunications.value.length,
    failed: failedCommunications.value.length,
    totalRecipients: totalRecipients.value,
    successRate: sentCommunications.value.length > 0 ?
      (sentCommunications.value.length / communications.value.length) * 100 : 0
  }))

  // Actions
  async function loadCommunications(params = {}) {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiService.getCommunications(params)
      communications.value = Array.isArray(response) ? response : []

      // Update pagination
      if (response.pagination) {
        pagination.value = {
          ...pagination.value,
          ...response.pagination
        }
      } else {
        pagination.value.total = communications.value.length
        pagination.value.totalPages = Math.ceil(communications.value.length / pagination.value.pageSize)
      }

    } catch (err) {
      error.value = err.message || 'Failed to load communications'
      console.error('Failed to load communications:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createCommunication(communicationData) {
    isLoading.value = true
    error.value = null

    try {
      const newCommunication = await apiService.createCommunication(communicationData)
      communications.value.unshift(newCommunication)
      currentCommunication.value = newCommunication

      return newCommunication
    } catch (err) {
      error.value = err.message || 'Failed to create communication'
      console.error('Failed to create communication:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function sendToAllMembers(communicationData) {
    isLoading.value = true
    error.value = null

    try {
      const result = await apiService.sendToAllMembers(communicationData)

      // Add to communications list if it's returned
      if (result.communication) {
        communications.value.unshift(result.communication)
        currentCommunication.value = result.communication
      }

      return result
    } catch (err) {
      error.value = err.message || 'Failed to send communication to all members'
      console.error('Failed to send communication to all members:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function sendToOverdueMembers(months, communicationData) {
    isLoading.value = true
    error.value = null

    try {
      const result = await apiService.sendToOverdueMembers(months, communicationData)

      // Add to communications list if it's returned
      if (result.communication) {
        communications.value.unshift(result.communication)
        currentCommunication.value = result.communication
      }

      return result
    } catch (err) {
      error.value = err.message || 'Failed to send communication to overdue members'
      console.error(`Failed to send communication to overdue members (${months} months):`, err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  function setFilters(newFilters) {
    filters.value = { ...filters.value, ...newFilters }
    pagination.value.page = 1 // Reset to first page when filters change
  }

  function setPagination(newPagination) {
    pagination.value = { ...pagination.value, ...newPagination }
  }

  function setCurrentCommunication(communication) {
    currentCommunication.value = communication
  }

  function clearCurrentCommunication() {
    currentCommunication.value = null
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    communications.value = []
    currentCommunication.value = null
    isLoading.value = false
    error.value = null
    filters.value = {
      type: 'all',
      status: 'all',
      dateRange: {
        start: null,
        end: null
      }
    }
    pagination.value = {
      page: 1,
      pageSize: 10,
      total: 0,
      totalPages: 0
    }
  }

  // Initialize store
  function initialize() {
    // Store initialization complete
  }

  // Expose state and actions
  return {
    // State
    communications,
    currentCommunication,
    isLoading,
    error,
    filters,
    pagination,

    // Getters
    sentCommunications,
    draftCommunications,
    failedCommunications,
    communicationsByType,
    totalRecipients,
    recentCommunications,
    filteredCommunications,
    paginatedCommunications,
    communicationStats,

    // Actions
    loadCommunications,
    createCommunication,
    sendToAllMembers,
    sendToOverdueMembers,
    setFilters,
    setPagination,
    setCurrentCommunication,
    clearCurrentCommunication,
    clearError,
    reset,
    initialize
  }
})
