import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiService from '@/services/api'

/**
 * Payment Store
 * Manages payment data, CRUD operations, and payment-related state
 */
export const usePaymentStore = defineStore('payments', () => {
  // State
  const payments = ref([])
  const currentPayment = ref(null)
  const isLoading = ref(false)
  const error = ref(null)
  const filters = ref({
    paymentMethod: 'all', // 'all', 'cash', 'card', 'bank_transfer'
    dateRange: {
      start: null,
      end: null
    },
    memberId: null
  })
  const pagination = ref({
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 0
  })

  // Getters
  const totalRevenue = computed(() =>
    payments.value.reduce((sum, payment) => sum + payment.amount, 0)
  )

  const recentPayments = computed(() =>
    payments.value
      .slice()
      .sort((a, b) => new Date(b.paymentDate) - new Date(a.paymentDate))
      .slice(0, 10)
  )

  const paymentsByMethod = computed(() => {
    const methods = {}
    payments.value.forEach(payment => {
      const method = payment.paymentMethod
      methods[method] = (methods[method] || 0) + 1
    })
    return methods
  })

  const monthlyRevenue = computed(() => {
    const monthly = {}
    payments.value.forEach(payment => {
      const date = new Date(payment.paymentDate)
      const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      monthly[monthKey] = (monthly[monthKey] || 0) + payment.amount
    })
    return monthly
  })

  const filteredPayments = computed(() => {
    let filtered = payments.value

    // Filter by payment method
    if (filters.value.paymentMethod !== 'all') {
      filtered = filtered.filter(payment =>
        payment.paymentMethod === filters.value.paymentMethod.toUpperCase()
      )
    }

    // Filter by date range
    if (filters.value.dateRange.start && filters.value.dateRange.end) {
      const startDate = new Date(filters.value.dateRange.start)
      const endDate = new Date(filters.value.dateRange.end)

      filtered = filtered.filter(payment => {
        const paymentDate = new Date(payment.paymentDate)
        return paymentDate >= startDate && paymentDate <= endDate
      })
    }

    // Filter by member
    if (filters.value.memberId) {
      filtered = filtered.filter(payment =>
        payment.memberId === filters.value.memberId
      )
    }

    return filtered
  })

  const paginatedPayments = computed(() => {
    const startIndex = (pagination.value.page - 1) * pagination.value.pageSize
    const endIndex = startIndex + pagination.value.pageSize
    return filteredPayments.value.slice(startIndex, endIndex)
  })

  const paymentStats = computed(() => ({
    total: payments.value.length,
    totalRevenue: totalRevenue.value,
    averagePayment: payments.value.length > 0 ? totalRevenue.value / payments.value.length : 0,
    recentCount: recentPayments.value.length
  }))

  // Actions
  async function loadPayments(params = {}) {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiService.getPayments(params)
      payments.value = Array.isArray(response) ? response : []

      // Update pagination
      if (response.pagination) {
        pagination.value = {
          ...pagination.value,
          ...response.pagination
        }
      } else {
        pagination.value.total = payments.value.length
        pagination.value.totalPages = Math.ceil(payments.value.length / pagination.value.pageSize)
      }

    } catch (err) {
      error.value = err.message || 'Failed to load payments'
      console.error('Failed to load payments:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function loadMemberPayments(memberId) {
    if (!memberId) {
      error.value = 'Member ID is required'
      return []
    }

    isLoading.value = true
    error.value = null

    try {
      const memberPayments = await apiService.getMemberPayments(memberId)
      return memberPayments
    } catch (err) {
      error.value = err.message || 'Failed to load member payments'
      console.error(`Failed to load payments for member ${memberId}:`, err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createPayment(paymentData) {
    isLoading.value = true
    error.value = null

    try {
      const newPayment = await apiService.createPayment(paymentData)
      payments.value.push(newPayment)
      currentPayment.value = newPayment

      return newPayment
    } catch (err) {
      error.value = err.message || 'Failed to create payment'
      console.error('Failed to create payment:', err)
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

  function setCurrentPayment(payment) {
    currentPayment.value = payment
  }

  function clearCurrentPayment() {
    currentPayment.value = null
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    payments.value = []
    currentPayment.value = null
    isLoading.value = false
    error.value = null
    filters.value = {
      paymentMethod: 'all',
      dateRange: {
        start: null,
        end: null
      },
      memberId: null
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
    payments,
    currentPayment,
    isLoading,
    error,
    filters,
    pagination,

    // Getters
    totalRevenue,
    recentPayments,
    paymentsByMethod,
    monthlyRevenue,
    filteredPayments,
    paginatedPayments,
    paymentStats,

    // Actions
    loadPayments,
    loadMemberPayments,
    createPayment,
    setFilters,
    setPagination,
    setCurrentPayment,
    clearCurrentPayment,
    clearError,
    reset,
    initialize
  }
})
