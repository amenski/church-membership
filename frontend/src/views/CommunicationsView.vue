<template>
  <div class="container mt-4">
    <h2>Communication Management</h2>
    
    <!-- Communication Form -->
    <div v-if="authStore.isStaff" class="card mb-4">
      <div class="card-body">
        <h5 class="card-title">Send New Message</h5>
        <form @submit.prevent="sendMessage">
          <div class="mb-3">
            <label class="form-label">Recipients</label>
            <select v-model="newMessage.recipientType" class="form-select" required>
              <option value="ALL">All Members</option>
              <option value="OVERDUE">Overdue Members</option>
              <option value="SPECIFIC">Specific Member</option>
            </select>
          </div>
          
          <div v-if="newMessage.recipientType === 'SPECIFIC'" class="mb-3">
            <label class="form-label">Select Member</label>
            <select v-model="newMessage.memberId" class="form-select" required>
              <option v-for="member in members" :key="member.id" :value="member.id">
                {{ member.name }}
              </option>
            </select>
          </div>
          
          <div v-if="newMessage.recipientType === 'OVERDUE'" class="mb-3">
            <label class="form-label">Months Overdue</label>
            <input v-model="newMessage.monthsOverdue" type="number" class="form-control" required>
          </div>
          
          <div class="mb-3">
            <label class="form-label">Subject</label>
            <input v-model="newMessage.subject" type="text" class="form-control" required>
          </div>
          
          <div class="mb-3">
            <label class="form-label">Message</label>
            <textarea v-model="newMessage.message" class="form-control" rows="4" required></textarea>
          </div>
          
          <button type="submit" class="btn btn-primary" :disabled="sending">
            <span v-if="sending" class="spinner-border spinner-border-sm me-1" role="status"></span>
            Send Message
          </button>
        </form>
      </div>
    </div>

    <!-- Communications List -->
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Recent Communications</h5>
        <div class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Title</th>
                <th>Recipients</th>
                <th>Delivery Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="comm in communications" :key="comm.id">
                <td>{{ formatDate(comm.sentDate) }}</td>
                <td>{{ comm.title }}</td>
                <td>{{ getRecipientInfo(comm) }}</td>
                <td>
                  <button
                    v-if="comm.sentDate"
                    class="btn btn-sm"
                    :class="getDeliveryStatusClass(comm)"
                    @click="showDeliveryDetails(comm)"
                  >
                    {{ getDeliverySummary(comm) }}
                  </button>
                  <span v-else class="text-muted">-</span>
                </td>
                <td>
                  <button
                    v-if="comm.sentDate"
                    class="btn btn-sm btn-info"
                    @click="showDeliveryDetails(comm)"
                  >
                    <i class="bi bi-envelope"></i> View
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Delivery Details Modal -->
    <div ref="deliveryModal" class="modal fade" tabindex="-1">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Delivery Details</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <div v-if="selectedCommunication" class="mb-3">
              <h6>{{ selectedCommunication.title }}</h6>
              <p class="text-muted">{{ formatDate(selectedCommunication.sentDate) }}</p>
            </div>
            
            <!-- Summary -->
            <div class="row mb-3">
              <div class="col-md-4">
                <div class="card bg-success text-white">
                  <div class="card-body">
                    <h6>Sent</h6>
                    <h3>{{ deliverySummary.sent }}</h3>
                  </div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="card bg-danger text-white">
                  <div class="card-body">
                    <h6>Failed</h6>
                    <h3>{{ deliverySummary.failed }}</h3>
                  </div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="card bg-warning text-white">
                  <div class="card-body">
                    <h6>Pending</h6>
                    <h3>{{ deliverySummary.pending }}</h3>
                  </div>
                </div>
              </div>
            </div>

            <!-- Delivery List -->
            <div class="table-responsive">
              <table class="table table-sm">
                <thead>
                  <tr>
                    <th>Recipient</th>
                    <th>Email</th>
                    <th>Status</th>
                    <th>Delivered At</th>
                    <th>Notes</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="delivery in deliveries" :key="delivery.id">
                    <td>{{ delivery.recipient?.name }}</td>
                    <td>{{ delivery.recipient?.email }}</td>
                    <td>
                      <span :class="getStatusBadgeClass(delivery.status)">
                        {{ delivery.status }}
                      </span>
                    </td>
                    <td>{{ formatDateTime(delivery.deliveryTime) }}</td>
                    <td>{{ delivery.responseNotes || '-' }}</td>
                    <td>
                      <button
                        v-if="delivery.status === 'FAILED' && authStore.isStaff"
                        type="button"
                        class="btn btn-sm btn-outline-primary"
                        :disabled="retryingIds.includes(delivery.id)"
                        @click="retryDelivery(delivery)"
                      >
                        <span
                          v-if="retryingIds.includes(delivery.id)"
                          class="spinner-border spinner-border-sm me-1"
                          role="status"
                        ></span>
                        Retry
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import * as bootstrap from 'bootstrap'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { buildCommunicationRequest } from '@/utils/communicationPayload'

export default {
  name: 'CommunicationsView',
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore()
    }
  },
  data() {
    return {
      sending: false,
      retryingIds: [],
      members: [],
      communications: [],
      deliveries: [],
      selectedCommunication: null,
      deliverySummary: {
        sent: 0,
        failed: 0,
        pending: 0
      },
      newMessage: {
        recipientType: 'ALL',
        memberId: null,
        monthsOverdue: 1,
        subject: '',
        message: ''
      }
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    async loadData() {
      try {
        const [membersRes, commsRes] = await Promise.all([
          api.getMembers(),
          api.getCommunications()
        ])
        this.members = membersRes.data || membersRes
        this.communications = commsRes.data || commsRes
      } catch (error) {
        console.error('Error loading data:', error)
      }
    },
    countRecipients() {
      if (this.newMessage.recipientType === 'ALL') {
        return this.members.filter(m => m.active).length
      }
      const months = Number(this.newMessage.monthsOverdue)
      return this.members.filter(m => m.consecutiveMonthsMissed >= months).length
    },
    async sendMessage() {
      const payload = buildCommunicationRequest(this.newMessage)
      const type = this.newMessage.recipientType

      if (type === 'ALL' || type === 'OVERDUE') {
        if (!window.confirm(`Send to ${this.countRecipients()} members?`)) {
          return
        }
      }

      this.sending = true
      try {
        if (type === 'ALL') {
          await api.sendToAllMembers(payload)
        } else if (type === 'OVERDUE') {
          await api.sendToOverdueMembers(this.newMessage.monthsOverdue, payload)
        } else {
          await api.sendToMember(this.newMessage.memberId, payload)
        }
        this.appStore.addNotification({
          type: 'success',
          title: 'Success',
          message: 'Sending started',
          isToast: true
        })
        await this.loadData()
        this.newMessage = {
          recipientType: 'ALL',
          memberId: null,
          monthsOverdue: 1,
          subject: '',
          message: ''
        }
      } catch (error) {
        this.appStore.addNotification({
          type: 'error',
          title: 'Error',
          message: error.message || 'Failed to send message',
          isToast: true
        })
      } finally {
        this.sending = false
      }
    },
    async showDeliveryDetails(communication) {
      this.selectedCommunication = communication
      try {
        const response = await api.getCommunicationDeliveries(communication.id)
        this.deliveries = response.data || response
        this.calculateSummary()
        new bootstrap.Modal(this.$refs.deliveryModal).show()
      } catch (error) {
        console.error('Error loading deliveries:', error)
        this.deliveries = []
      }
    },
    calculateSummary() {
      this.deliverySummary = {
        sent: this.deliveries.filter(d => d.status === 'SENT').length,
        failed: this.deliveries.filter(d => d.status === 'FAILED').length,
        pending: this.deliveries.filter(d => d.status === 'PENDING').length
      }
    },
    async retryDelivery(delivery) {
      this.retryingIds.push(delivery.id)
      try {
        const response = await api.retryDelivery(this.selectedCommunication.id, delivery.id)
        const updated = response.data || response
        this.deliveries = this.deliveries.map(d => (d.id === updated.id ? updated : d))
        this.calculateSummary()
        if (updated.status === 'SENT') {
          this.appStore.addNotification({
            type: 'success',
            title: 'Success',
            message: 'Delivery re-sent',
            isToast: true
          })
        } else {
          this.appStore.addNotification({
            type: 'warning',
            title: 'Warning',
            message: 'Retry failed',
            isToast: true
          })
        }
      } catch (error) {
        this.appStore.addNotification({
          type: 'error',
          title: 'Error',
          message: error.message || 'Failed to retry delivery',
          isToast: true
        })
      } finally {
        this.retryingIds = this.retryingIds.filter(id => id !== delivery.id)
      }
    },
    getDeliverySummary(comm) {
      // If we already have deliveries in the communication object
      if (comm.deliveries && comm.deliveries.length > 0) {
        const sent = comm.deliveries.filter(d => d.status === 'SENT').length
        const failed = comm.deliveries.filter(d => d.status === 'FAILED').length
        return `${sent} sent, ${failed} failed`
      }
      return 'Click to view'
    },
    getDeliveryStatusClass(comm) {
      // Default status if not loaded yet
      return 'btn-secondary'
    },
    formatDate(date) {
      if (!date) return '-'
      return new Date(date).toLocaleDateString()
    },
    formatDateTime(dateTime) {
      if (!dateTime) return '-'
      return new Date(dateTime).toLocaleString()
    },
    getRecipientInfo(comm) {
      if (comm.sentToAllMembers) {
        return 'All Members'
      }
      if (comm.memberId) {
        const member = this.members.find(m => m.id === comm.memberId)
        return member ? member.name : 'Unknown'
      }
      return '-'
    },
    getStatusBadgeClass(status) {
      const classes = {
        'SENT': 'badge bg-success',
        'FAILED': 'badge bg-danger',
        'PENDING': 'badge bg-warning',
        'DELIVERED': 'badge bg-info'
      }
      return classes[status] || 'badge bg-secondary'
    }
  }
}
</script>
