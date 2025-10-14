<template>
  <div class="container mt-4">
    <!-- Stats Cards -->
    <div class="row mb-4">
      <div class="col-md-3">
        <div class="card bg-primary text-white">
          <div class="card-body">
            <h5 class="card-title">Total Members</h5>
            <h2>{{ stats.totalMembers }}</h2>
          </div>
        </div>
      </div>
      <div class="col-md-3">
        <div class="card bg-success text-white">
          <div class="card-body">
            <h5 class="card-title">Active Members</h5>
            <h2>{{ stats.activeMembers }}</h2>
          </div>
        </div>
      </div>
      <div class="col-md-3">
        <div class="card bg-warning text-white">
          <div class="card-body">
            <h5 class="card-title">Overdue Members</h5>
            <h2>{{ stats.overdueMembers }}</h2>
          </div>
        </div>
      </div>
      <div class="col-md-3">
        <div class="card bg-info text-white">
          <div class="card-body">
            <h5 class="card-title">This Month's Revenue</h5>
            <h2>${{ stats.monthlyRevenue }}</h2>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <!-- Recent Payments -->
      <div class="col-md-6 mb-4">
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
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="payment in recentPayments" :key="payment.id">
                    <td>{{ formatDate(payment.paymentDate) }}</td>
                    <td>{{ getMemberName(payment.memberId) }}</td>
                    <td>${{ payment.amount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Overdue Members -->
      <div class="col-md-6 mb-4">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Overdue Members</h5>
            <div class="table-responsive">
              <table class="table">
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Months Overdue</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="member in overdueMembers" :key="member.id">
                    <td>{{ member.name }}</td>
                    <td>{{ member.consecutiveMonthsMissed }}</td>
                    <td>
                      <button @click="sendReminder(member)" class="btn btn-sm btn-warning">
                        Send Reminder
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Activity Timeline -->
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Recent Activity</h5>
            <div class="timeline">
              <div v-for="activity in activities" :key="activity.id" class="timeline-item">
                <div class="timeline-date">{{ formatDate(activity.date) }}</div>
                <div class="timeline-content">
                  <div class="timeline-icon" :class="activity.type">
                    <i :class="getActivityIcon(activity.type)"></i>
                  </div>
                  <div class="timeline-text">{{ activity.description }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'

export default {
  name: 'DashboardView',
  data() {
    return {
      stats: {
        totalMembers: 0,
        activeMembers: 0,
        overdueMembers: 0,
        monthlyRevenue: 0
      },
      recentPayments: [],
      overdueMembers: [],
      activities: [],
      members: []
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    async loadData() {
      try {
        const [
          statsRes,
          paymentsRes,
          overdueRes,
          activitiesRes,
          membersRes
        ] = await Promise.all([
          api.getDashboardStats(),
          api.getRecentPayments(),
          api.getOverdueMembers(),
          api.getRecentActivities(),
          api.getMembers()
        ])
        
        this.stats = statsRes.data
        this.recentPayments = paymentsRes.data
        this.overdueMembers = overdueRes.data
        this.activities = activitiesRes.data
        this.members = membersRes.data
      } catch (error) {
        console.error('Error loading dashboard data:', error)
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    getMemberName(memberId) {
      const member = this.members.find(m => m.id === memberId)
      return member ? member.name : 'Unknown'
    },
    async sendReminder(member) {
      try {
        await api.createCommunication({
          memberId: member.id,
          subject: 'Payment Reminder',
          message: `Dear ${member.name}, this is a reminder that your membership payment is overdue.`
        })
        await this.loadData()
      } catch (error) {
        console.error('Error sending reminder:', error)
      }
    },
    getActivityIcon(type) {
      const icons = {
        payment: 'fas fa-dollar-sign',
        member: 'fas fa-user',
        communication: 'fas fa-envelope'
      }
      return icons[type] || 'fas fa-info-circle'
    }
  }
}
</script>

<style scoped>
.timeline {
  position: relative;
  padding: 20px 0;
}

.timeline-item {
  display: flex;
  margin-bottom: 20px;
}

.timeline-date {
  width: 100px;
  padding-right: 20px;
  text-align: right;
}

.timeline-content {
  display: flex;
  align-items: center;
  flex: 1;
  padding-left: 20px;
  border-left: 2px solid #e9ecef;
}

.timeline-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  color: white;
}

.timeline-icon.payment { background-color: #28a745; }
.timeline-icon.member { background-color: #007bff; }
.timeline-icon.communication { background-color: #17a2b8; }
</style>