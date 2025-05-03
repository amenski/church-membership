<template>
  <div class="container mt-4">
    <h2>Communication Management</h2>
    
    <!-- Communication Form -->
    <div class="card mb-4">
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
          
          <div class="mb-3" v-if="newMessage.recipientType === 'SPECIFIC'">
            <label class="form-label">Select Member</label>
            <select v-model="newMessage.memberId" class="form-select" required>
              <option v-for="member in members" :key="member.id" :value="member.id">
                {{ member.name }}
              </option>
            </select>
          </div>
          
          <div class="mb-3" v-if="newMessage.recipientType === 'OVERDUE'">
            <label class="form-label">Months Overdue</label>
            <input type="number" v-model="newMessage.monthsOverdue" class="form-control" required>
          </div>
          
          <div class="mb-3">
            <label class="form-label">Subject</label>
            <input type="text" v-model="newMessage.subject" class="form-control" required>
          </div>
          
          <div class="mb-3">
            <label class="form-label">Message</label>
            <textarea v-model="newMessage.message" class="form-control" rows="4" required></textarea>
          </div>
          
          <button type="submit" class="btn btn-primary">Send Message</button>
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
                <th>Recipient</th>
                <th>Subject</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="comm in communications" :key="comm.id">
                <td>{{ formatDate(comm.sentDate) }}</td>
                <td>{{ getRecipientInfo(comm) }}</td>
                <td>{{ comm.subject }}</td>
                <td>{{ comm.isRead ? 'Read' : 'Unread' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'

export default {
  name: 'CommunicationsView',
  data() {
    return {
      members: [],
      communications: [],
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
        this.members = membersRes.data
        this.communications = commsRes.data
      } catch (error) {
        console.error('Error loading data:', error)
      }
    },
    async sendMessage() {
      try {
        if (this.newMessage.recipientType === 'ALL') {
          await api.sendToAllMembers(this.newMessage)
        } else if (this.newMessage.recipientType === 'OVERDUE') {
          await api.sendToOverdueMembers(this.newMessage.monthsOverdue, this.newMessage)
        } else {
          await api.createCommunication(this.newMessage)
        }
        await this.loadData()
        this.newMessage = {
          recipientType: 'ALL',
          memberId: null,
          monthsOverdue: 1,
          subject: '',
          message: ''
        }
      } catch (error) {
        console.error('Error sending message:', error)
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    getRecipientInfo(comm) {
      if (comm.memberId) {
        const member = this.members.find(m => m.id === comm.memberId)
        return member ? member.name : 'Unknown'
      }
      return comm.recipientType
    }
  }
}
</script> 