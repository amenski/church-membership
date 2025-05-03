<template>
  <div class="container mt-4">
    <h2>Payment Management</h2>
    
    <!-- Payment Form -->
    <div class="card mb-4">
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
            <input type="number" v-model="newPayment.amount" class="form-control" required>
          </div>
          <div class="mb-3">
            <label class="form-label">Payment Method</label>
            <select v-model="newPayment.paymentMethod" class="form-select" required>
              <option value="CASH">Cash</option>
              <option value="BANK_TRANSFER">Bank Transfer</option>
              <option value="MOBILE_PAYMENT">Mobile Payment</option>
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
                <td>{{ getMemberName(payment.memberId) }}</td>
                <td>${{ payment.amount }}</td>
                <td>{{ payment.paymentMethod }}</td>
                <td>{{ payment.receiptNumber }}</td>
                <td>
                  <button @click="generateReceipt(payment)" class="btn btn-sm btn-primary">
                    <i class="fas fa-file-pdf"></i> Receipt
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Receipt Modal -->
    <div class="modal fade" id="receiptModal" tabindex="-1">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Payment Receipt</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body" ref="receiptContent">
            <div v-if="selectedPayment" class="receipt-container">
              <div class="text-center mb-4">
                <h3>NGO Member Management</h3>
                <p>Payment Receipt</p>
              </div>
              
              <div class="row mb-3">
                <div class="col-6">
                  <strong>Receipt Number:</strong><br>
                  {{ selectedPayment.receiptNumber }}
                </div>
                <div class="col-6 text-end">
                  <strong>Date:</strong><br>
                  {{ formatDate(selectedPayment.paymentDate) }}
                </div>
              </div>
              
              <div class="row mb-3">
                <div class="col-12">
                  <strong>Received From:</strong><br>
                  {{ getMemberName(selectedPayment.memberId) }}
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
              <i class="fas fa-download"></i> Download PDF
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import html2pdf from 'html2pdf.js'

export default {
  name: 'PaymentsView',
  data() {
    return {
      members: [],
      payments: [],
      analytics: {
        totalRevenue: 0,
        monthlyRevenue: 0,
        averagePayment: 0
      },
      newPayment: {
        memberId: null,
        amount: null,
        paymentMethod: 'CASH'
      },
      selectedPayment: null
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    async loadData() {
      try {
        const [membersRes, paymentsRes, analyticsRes] = await Promise.all([
          api.getMembers(),
          api.getPayments(),
          api.getPaymentAnalytics()
        ])
        this.members = membersRes.data
        this.payments = paymentsRes.data
        this.analytics = analyticsRes.data
      } catch (error) {
        console.error('Error loading data:', error)
      }
    },
    async submitPayment() {
      try {
        const response = await api.createPayment(this.newPayment)
        await this.loadData()
        this.newPayment = {
          memberId: null,
          amount: null,
          paymentMethod: 'CASH'
        }
        this.generateReceipt(response.data)
      } catch (error) {
        console.error('Error creating payment:', error)
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    getMemberName(memberId) {
      const member = this.members.find(m => m.id === memberId)
      return member ? member.name : 'Unknown'
    },
    generateReceipt(payment) {
      this.selectedPayment = payment
      new bootstrap.Modal(document.getElementById('receiptModal')).show()
    },
    async downloadReceipt() {
      const element = this.$refs.receiptContent
      const options = {
        margin: 1,
        filename: `receipt-${this.selectedPayment.receiptNumber}.pdf`,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' }
      }
      await html2pdf().set(options).from(element).save()
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