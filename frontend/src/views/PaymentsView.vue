<template>
  <div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2>Payment Management</h2>
      <button class="btn btn-success" @click="exportPayments">
        <i class="bi bi-file-earmark-spreadsheet"></i> Export CSV
      </button>
    </div>

    <!-- Payment Form -->
    <div v-if="authStore.isStaff" class="card mb-4">
      <div class="card-body">
        <h5 class="card-title">Record New Payment</h5>
        <form @submit.prevent="submitPayment">
          <div class="mb-3">
            <label class="form-label">Member</label>
            <select v-model="newPayment.memberId" class="form-select" required>
              <option v-for="member in members" :key="member.id" :value="member.id">
                {{ member.name }}
              </option>
            </select>
          </div>
          <div class="mb-3">
            <label class="form-label">Amount</label>
            <input v-model="newPayment.amount" type="number" class="form-control" required>
          </div>
          <div class="mb-3">
            <label class="form-label">Payment month</label>
            <input v-model="newPayment.period" type="month" class="form-control" required>
          </div>
          <div class="mb-3">
            <label class="form-label">Payment Method</label>
            <select v-model="newPayment.paymentMethod" class="form-select" required>
              <option v-for="method in paymentMethods" :key="method.value" :value="method.value">
                {{ method.label }}
              </option>
            </select>
          </div>
          <button type="submit" class="btn btn-primary">Record Payment</button>
        </form>
      </div>
    </div>

    <!-- Payment Analytics -->
    <div class="row mb-4">
      <div class="col-md-4">
        <div class="card bg-primary text-white">
          <div class="card-body">
            <h5 class="card-title">Total Revenue</h5>
            <h2>${{ analytics.totalRevenue }}</h2>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card bg-success text-white">
          <div class="card-body">
            <h5 class="card-title">This Month</h5>
            <h2>${{ analytics.monthlyRevenue }}</h2>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card bg-info text-white">
          <div class="card-body">
            <h5 class="card-title">Average Payment</h5>
            <h2>${{ analytics.averagePayment }}</h2>
          </div>
        </div>
      </div>
    </div>

    <!-- Payments List -->
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Recent Payments</h5>
        <div class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Member</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Receipt</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="payment in payments" :key="payment.id">
                <td>{{ formatDate(payment.paymentDate) }}</td>
                <td>{{ payment.member?.name || 'Unknown' }}</td>
                <td>${{ payment.amount }}</td>
                <td>{{ payment.paymentMethod }}</td>
                <td>{{ receiptNumber(payment) }}</td>
                <td>
                  <button class="btn btn-sm btn-primary" @click="generateReceipt(payment)">
                    <i class="bi bi-file-earmark-pdf"></i> Receipt
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Receipt Modal -->
    <div ref="receiptModal" class="modal fade" tabindex="-1">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Payment Receipt</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div ref="receiptContent" class="modal-body">
            <div v-if="selectedPayment" class="receipt-container">
              <div class="text-center mb-4">
                <h3>Member Management</h3>
                <p>Payment Receipt</p>
              </div>

              <div class="row mb-3">
                <div class="col-6">
                  <strong>Receipt Number:</strong><br>
                  {{ receiptNumber(selectedPayment) }}
                </div>
                <div class="col-6 text-end">
                  <strong>Date:</strong><br>
                  {{ formatDate(selectedPayment.paymentDate) }}
                </div>
              </div>

              <div class="row mb-3">
                <div class="col-12">
                  <strong>Received From:</strong><br>
                  {{ selectedPayment.member?.name || 'Unknown' }}
                </div>
              </div>

              <div class="row mb-3">
                <div class="col-6">
                  <strong>Amount:</strong><br>
                  ${{ selectedPayment.amount }}
                </div>
                <div class="col-6">
                  <strong>Payment Method:</strong><br>
                  {{ selectedPayment.paymentMethod }}
                </div>
              </div>

              <div class="row mt-5">
                <div class="col-6">
                  <div class="border-top">
                    <p class="text-center mt-2">Received By</p>
                  </div>
                </div>
                <div class="col-6">
                  <div class="border-top">
                    <p class="text-center mt-2">Member Signature</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button type="button" class="btn btn-primary" @click="downloadReceipt">
              <i class="bi bi-download"></i> Download PDF
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import * as bootstrap from 'bootstrap'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob } from '@/utils'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'

const currentMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
const emptyPayment = () => ({
  memberId: null,
  amount: null,
  paymentMethod: 'CASH',
  period: currentMonth()
})

export default {
  name: 'PaymentsView',
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore()
    }
  },
  data() {
    return {
      members: [],
      payments: [],
      analytics: {
        totalRevenue: 0,
        monthlyRevenue: 0,
        averagePayment: 0
      },
      paymentMethods: PAYMENT_METHODS,
      newPayment: emptyPayment(),
      selectedPayment: null
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    async loadData() {
      try {
        // Load members and payments
        const [members, payments] = await Promise.all([
          api.getMembers(),
          api.getPayments()
        ])

        // Ensure data is always an array
        this.members = Array.isArray(members) ? members : []
        this.payments = Array.isArray(payments) ? payments : []

        // Calculate analytics from payment data
        this.calculateAnalytics()
      } catch (error) {
        console.error('Error loading data:', error)
        this.members = []
        this.payments = []
        this.notifyError('Could not load payments', error, 'Could not load payments')
      }
    },
    calculateAnalytics() {
      if (!this.payments || this.payments.length === 0) {
        this.analytics = {
          totalRevenue: 0,
          monthlyRevenue: 0,
          averagePayment: 0
        }
        return
      }

      const now = new Date()
      const currentMonth = now.getMonth()
      const currentYear = now.getFullYear()

      // Calculate total revenue
      const totalRevenue = this.payments.reduce((sum, p) => sum + (p.amount || 0), 0)

      // Calculate monthly revenue
      const monthlyRevenue = this.payments
        .filter(p => {
          const paymentDate = new Date(p.paymentDate)
          return paymentDate.getMonth() === currentMonth &&
                 paymentDate.getFullYear() === currentYear
        })
        .reduce((sum, p) => sum + (p.amount || 0), 0)

      // Calculate average payment
      const averagePayment = this.payments.length > 0
        ? totalRevenue / this.payments.length
        : 0

      this.analytics = {
        totalRevenue: totalRevenue.toFixed(2),
        monthlyRevenue: monthlyRevenue.toFixed(2),
        averagePayment: averagePayment.toFixed(2)
      }
    },
    async submitPayment() {
      try {
        const payment = await api.createPayment(buildPaymentRequest(this.newPayment))
        await this.loadData()
        this.newPayment = emptyPayment()
        // Generate receipt for the newly created payment
        if (payment) {
          this.generateReceipt(payment)
        }
      } catch (error) {
        console.error('Error creating payment:', error)
        this.notifyError('Payment failed', error, 'Could not record the payment')
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    receiptNumber(payment) {
      return `R-${String(payment.id).padStart(6, '0')}`
    },
    notifyError(title, error, fallback) {
      this.appStore.addNotification({
        type: 'error',
        title,
        message: error.message || fallback,
        isToast: true
      })
    },
    generateReceipt(payment) {
      this.selectedPayment = payment
      new bootstrap.Modal(this.$refs.receiptModal).show()
    },
    async downloadReceipt() {
      const element = this.$refs.receiptContent
      const options = {
        margin: 1,
        filename: `receipt-${this.receiptNumber(this.selectedPayment)}.pdf`,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' }
      }
      const { default: html2pdf } = await import('html2pdf.js')
      await html2pdf().set(options).from(element).save()
    },
    async exportPayments() {
      try {
        const response = await api.exportPayments()
        // api.request() already returns response.data (the blob)
        downloadBlob(response, `payments_${new Date().toISOString().split('T')[0]}.csv`)
      } catch (error) {
        console.error('Error exporting payments:', error)
        this.appStore.addNotification({
          type: 'error',
          title: 'Export failed',
          message: error.message || 'Could not export CSV',
          isToast: true
        })
      }
    }
  }
}
</script>

<style scoped>
.receipt-container {
  padding: 20px;
  background: white;
}
</style>
