import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiService from '@/services/api'

/**
 * Member Store
 * Manages member data, CRUD operations, and member-related state
 */
export const useMemberStore = defineStore('members', () => {
  // State
  const members = ref([])
  const currentMember = ref(null)
  const isLoading = ref(false)
  const error = ref(null)
  const filters = ref({
    status: 'all', // 'all', 'active', 'inactive'
    paymentStatus: 'all', // 'all', 'current', 'overdue'
    searchQuery: ''
  })
  const pagination = ref({
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 0
  })

  // Getters
  const activeMembers = computed(() =>
    members.value.filter(member => member.status === 'ACTIVE')
  )

  const inactiveMembers = computed(() =>
    members.value.filter(member => member.status === 'INACTIVE')
  )

  const overdueMembers = computed(() =>
    members.value.filter(member => member.paymentStatus === 'OVERDUE')
  )

  const filteredMembers = computed(() => {
    let filtered = members.value

    // Filter by status
    if (filters.value.status !== 'all') {
      filtered = filtered.filter(member =>
        member.status === filters.value.status.toUpperCase()
      )
    }

    // Filter by payment status
    if (filters.value.paymentStatus !== 'all') {
      filtered = filtered.filter(member =>
        member.paymentStatus === filters.value.paymentStatus.toUpperCase()
      )
    }

    // Filter by search query
    if (filters.value.searchQuery) {
      const query = filters.value.searchQuery.toLowerCase()
      filtered = filtered.filter(member =>
        member.firstName.toLowerCase().includes(query) ||
        member.lastName.toLowerCase().includes(query) ||
        member.email.toLowerCase().includes(query) ||
        member.phone.includes(query)
      )
    }

    return filtered
  })

  const paginatedMembers = computed(() => {
    const startIndex = (pagination.value.page - 1) * pagination.value.pageSize
    const endIndex = startIndex + pagination.value.pageSize
    return filteredMembers.value.slice(startIndex, endIndex)
  })

  const memberStats = computed(() => ({
    total: members.value.length,
    active: activeMembers.value.length,
    inactive: inactiveMembers.value.length,
    overdue: overdueMembers.value.length,
    current: members.value.length - overdueMembers.value.length
  }))

  // Actions
  async function loadMembers(params = {}) {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiService.getMembers(params)
      members.value = Array.isArray(response) ? response : []

      // Update pagination
      if (response.pagination) {
        pagination.value = {
          ...pagination.value,
          ...response.pagination
        }
      } else {
        pagination.value.total = members.value.length
        pagination.value.totalPages = Math.ceil(members.value.length / pagination.value.pageSize)
      }

    } catch (err) {
      error.value = err.message || 'Failed to load members'
      console.error('Failed to load members:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function loadMemberById(id) {
    if (!id) {
      error.value = 'Member ID is required'
      return null
    }

    isLoading.value = true
    error.value = null

    try {
      const member = await apiService.getMemberById(id)
      currentMember.value = member
      return member
    } catch (err) {
      error.value = err.message || 'Failed to load member'
      console.error(`Failed to load member ${id}:`, err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createMember(memberData) {
    isLoading.value = true
    error.value = null

    try {
      const newMember = await apiService.createMember(memberData)
      members.value.push(newMember)
      currentMember.value = newMember

      return newMember
    } catch (err) {
      error.value = err.message || 'Failed to create member'
      console.error('Failed to create member:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateMember(id, memberData) {
    if (!id) {
      error.value = 'Member ID is required'
      return null
    }

    isLoading.value = true
    error.value = null

    try {
      const updatedMember = await apiService.updateMember(id, memberData)

      // Update in local state
      const index = members.value.findIndex(m => m.id === id)
      if (index !== -1) {
        members.value[index] = updatedMember
      }

      // Update current member if it's the one being edited
      if (currentMember.value && currentMember.value.id === id) {
        currentMember.value = updatedMember
      }

      return updatedMember
    } catch (err) {
      error.value = err.message || 'Failed to update member'
      console.error(`Failed to update member ${id}:`, err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteMember(id) {
    if (!id) {
      error.value = 'Member ID is required'
      return false
    }

    isLoading.value = true
    error.value = null

    try {
      await apiService.deleteMember(id)

      // Remove from local state
      const index = members.value.findIndex(m => m.id === id)
      if (index !== -1) {
        members.value.splice(index, 1)
      }

      // Clear current member if it's the one being deleted
      if (currentMember.value && currentMember.value.id === id) {
        currentMember.value = null
      }

      return true
    } catch (err) {
      error.value = err.message || 'Failed to delete member'
      console.error(`Failed to delete member ${id}:`, err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function loadOverdueMembers(months = 3) {
    isLoading.value = true
    error.value = null

    try {
      const overdueMembers = await apiService.getOverdueMembers(months)
      return overdueMembers
    } catch (err) {
      error.value = err.message || 'Failed to load overdue members'
      console.error('Failed to load overdue members:', err)
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

  function setCurrentMember(member) {
    currentMember.value = member
  }

  function clearCurrentMember() {
    currentMember.value = null
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    members.value = []
    currentMember.value = null
    isLoading.value = false
    error.value = null
    filters.value = {
      status: 'all',
      paymentStatus: 'all',
      searchQuery: ''
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
    members,
    currentMember,
    isLoading,
    error,
    filters,
    pagination,

    // Getters
    activeMembers,
    inactiveMembers,
    overdueMembers,
    filteredMembers,
    paginatedMembers,
    memberStats,

    // Actions
    loadMembers,
    loadMemberById,
    createMember,
    updateMember,
    deleteMember,
    loadOverdueMembers,
    setFilters,
    setPagination,
    setCurrentMember,
    clearCurrentMember,
    clearError,
    reset,
    initialize
  }
})
