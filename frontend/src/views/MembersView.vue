<template>
  <div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2>Member Management</h2>
      <button class="btn btn-primary" @click="showAddModal">
        <i class="fas fa-plus"></i> Add Member
      </button>
    </div>

    <!-- Search and Filter -->
    <div class="card mb-4">
      <div class="card-body">
        <div class="row">
          <div class="col-md-4">
            <input type="text" v-model="filters.search" class="form-control" placeholder="Search members...">
          </div>
          <div class="col-md-3">
            <select v-model="filters.status" class="form-select">
              <option value="ALL">All Status</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
          <div class="col-md-3">
            <select v-model="filters.paymentStatus" class="form-select">
              <option value="ALL">All Payment Status</option>
              <option value="CURRENT">Current</option>
              <option value="OVERDUE">Overdue</option>
            </select>
          </div>
        </div>
      </div>
    </div>

    <!-- Members Table -->
    <div class="card">
      <div class="card-body">
        <div class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Join Date</th>
                <th>Status</th>
                <th>Last Payment</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in filteredMembers" :key="member.id">
                <td>{{ member.name }}</td>
                <td>{{ member.email }}</td>
                <td>{{ member.phone }}</td>
                <td>{{ formatDate(member.joinDate) }}</td>
                <td>
                  <span :class="getStatusBadgeClass(member)">
                    {{ member.active ? 'Active' : 'Inactive' }}
                  </span>
                </td>
                <td>
                  <span :class="getPaymentStatusClass(member)">
                    {{ getPaymentStatus(member) }}
                  </span>
                </td>
                <td>
                  <div class="btn-group">
                    <button @click="showEditModal(member)" class="btn btn-sm btn-primary">
                      <i class="fas fa-edit"></i>
                    </button>
                    <button @click="showDeleteModal(member)" class="btn btn-sm btn-danger">
                      <i class="fas fa-trash"></i>
                    </button>
                    <button @click="toggleStatus(member)" class="btn btn-sm" :class="member.active ? 'btn-warning' : 'btn-success'">
                      <i :class="member.active ? 'fas fa-ban' : 'fas fa-check'"></i>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Add/Edit Member Modal -->
    <div class="modal fade" id="memberModal" tabindex="-1">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ editingMember ? 'Edit' : 'Add' }} Member</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="saveMember">
              <div class="mb-3">
                <label class="form-label">Name</label>
                <input type="text" v-model="memberForm.name" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Email</label>
                <input type="email" v-model="memberForm.email" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Phone</label>
                <input type="tel" v-model="memberForm.phone" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Address</label>
                <textarea v-model="memberForm.address" class="form-control" rows="2"></textarea>
              </div>
              <div class="mb-3">
                <div class="form-check">
                  <input type="checkbox" v-model="memberForm.active" class="form-check-input" id="activeStatus">
                  <label class="form-check-label" for="activeStatus">Active Member</label>
                </div>
              </div>
              <div class="text-end">
                <button type="button" class="btn btn-secondary me-2" data-bs-dismiss="modal">Cancel</button>
                <button type="submit" class="btn btn-primary">Save</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div class="modal fade" id="deleteModal" tabindex="-1">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Delete Member</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            Are you sure you want to delete {{ selectedMember?.name }}? This action cannot be undone.
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-danger" @click="deleteMember">Delete</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'

export default {
  name: 'MembersView',
  data() {
    return {
      members: [],
      filters: {
        search: '',
        status: 'ALL',
        paymentStatus: 'ALL'
      },
      memberForm: {
        name: '',
        email: '',
        phone: '',
        address: '',
        active: true
      },
      editingMember: null,
      selectedMember: null
    }
  },
  computed: {
    filteredMembers() {
      return this.members.filter(member => {
        const matchesSearch = !this.filters.search || 
          member.name.toLowerCase().includes(this.filters.search.toLowerCase()) ||
          member.email.toLowerCase().includes(this.filters.search.toLowerCase())

        const matchesStatus = this.filters.status === 'ALL' || 
          (this.filters.status === 'ACTIVE' && member.active) ||
          (this.filters.status === 'INACTIVE' && !member.active)

        const matchesPayment = this.filters.paymentStatus === 'ALL' ||
          (this.filters.paymentStatus === 'OVERDUE' && member.consecutiveMonthsMissed > 0) ||
          (this.filters.paymentStatus === 'CURRENT' && member.consecutiveMonthsMissed === 0)

        return matchesSearch && matchesStatus && matchesPayment
      })
    }
  },
  async created() {
    await this.loadMembers()
  },
  methods: {
    async loadMembers() {
      try {
        const response = await api.getMembers()
        this.members = response.data
      } catch (error) {
        console.error('Error loading members:', error)
      }
    },
    showAddModal() {
      this.editingMember = null
      this.memberForm = {
        name: '',
        email: '',
        phone: '',
        address: '',
        active: true
      }
      new bootstrap.Modal(document.getElementById('memberModal')).show()
    },
    showEditModal(member) {
      this.editingMember = member
      this.memberForm = { ...member }
      new bootstrap.Modal(document.getElementById('memberModal')).show()
    },
    showDeleteModal(member) {
      this.selectedMember = member
      new bootstrap.Modal(document.getElementById('deleteModal')).show()
    },
    async saveMember() {
      try {
        if (this.editingMember) {
          await api.updateMember(this.editingMember.id, this.memberForm)
        } else {
          await api.createMember(this.memberForm)
        }
        await this.loadMembers()
        bootstrap.Modal.getInstance(document.getElementById('memberModal')).hide()
      } catch (error) {
        console.error('Error saving member:', error)
      }
    },
    async deleteMember() {
      try {
        await api.deleteMember(this.selectedMember.id)
        await this.loadMembers()
        bootstrap.Modal.getInstance(document.getElementById('deleteModal')).hide()
      } catch (error) {
        console.error('Error deleting member:', error)
      }
    },
    async toggleStatus(member) {
      try {
        await api.updateMember(member.id, { ...member, active: !member.active })
        await this.loadMembers()
      } catch (error) {
        console.error('Error toggling member status:', error)
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    getStatusBadgeClass(member) {
      return `badge ${member.active ? 'bg-success' : 'bg-danger'}`
    },
    getPaymentStatusClass(member) {
      return `badge ${member.consecutiveMonthsMissed > 0 ? 'bg-warning' : 'bg-success'}`
    },
    getPaymentStatus(member) {
      return member.consecutiveMonthsMissed > 0 
        ? `${member.consecutiveMonthsMissed} months overdue`
        : 'Current'
    }
  }
}
</script>

<style scoped>
.btn-group .btn {
  padding: 0.25rem 0.5rem;
}
.badge {
  font-size: 0.875rem;
}
</style>