<template>
  <div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2>Member Management</h2>
      <div>
        <button class="btn btn-success me-2" @click="exportMembers">
          <i class="bi bi-file-earmark-spreadsheet"></i> Export CSV
        </button>
        <button class="btn btn-primary" @click="showAddModal">
          <i class="bi bi-plus"></i> Add Member
        </button>
      </div>
    </div>

    <!-- Search and Filter -->
    <div class="card mb-4">
      <div class="card-body">
        <div class="row">
          <div class="col-md-4">
            <input v-model="filters.search" type="text" class="form-control" placeholder="Search name, email or phone">
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
        <div class="row mt-3">
          <div class="col-md-3">
            <label class="form-label small mb-1" for="joinedFrom">Joined from</label>
            <input id="joinedFrom" v-model="filters.joinedFrom" type="date" class="form-control">
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1" for="joinedTo">Joined to</label>
            <input id="joinedTo" v-model="filters.joinedTo" type="date" class="form-control">
          </div>
          <div v-if="hasActiveFilters" class="col-md-3 d-flex align-items-end">
            <button type="button" class="btn btn-outline-secondary" @click="clearFilters">
              <i class="bi bi-x-circle"></i> Clear filters
            </button>
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
                <th role="button" tabindex="0" @click="setSort('name')" @keydown.enter="setSort('name')">
                  Name <i v-if="sort.key === 'name'" :class="sortIcon"></i>
                </th>
                <th>Email</th>
                <th>Phone</th>
                <th role="button" tabindex="0" @click="setSort('joinDate')" @keydown.enter="setSort('joinDate')">
                  Join Date <i v-if="sort.key === 'joinDate'" :class="sortIcon"></i>
                </th>
                <th>Status</th>
                <th role="button" tabindex="0" @click="setSort('consecutiveMonthsMissed')" @keydown.enter="setSort('consecutiveMonthsMissed')">
                  Last Payment <i v-if="sort.key === 'consecutiveMonthsMissed'" :class="sortIcon"></i>
                </th>
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
                    <button class="btn btn-sm btn-primary" @click="showEditModal(member)">
                      <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" @click="showDeleteModal(member)">
                      <i class="bi bi-trash"></i>
                    </button>
                    <button class="btn btn-sm" :class="member.active ? 'btn-warning' : 'btn-success'" @click="toggleStatus(member)">
                      <i :class="member.active ? 'bi bi-slash-circle' : 'bi bi-check-circle'"></i>
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
    <div ref="memberModal" class="modal fade" tabindex="-1">
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
                <input v-model="memberForm.name" type="text" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Email</label>
                <input v-model="memberForm.email" type="email" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Phone</label>
                <input v-model="memberForm.phone" type="tel" class="form-control" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Address</label>
                <textarea v-model="memberForm.address" class="form-control" rows="2"></textarea>
              </div>
              <div class="mb-3">
                <div class="form-check">
                  <input id="activeStatus" v-model="memberForm.active" type="checkbox" class="form-check-input">
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
    <div ref="deleteModal" class="modal fade" tabindex="-1">
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
import * as bootstrap from 'bootstrap'
import { useAppStore } from '../stores/appStore'
import { downloadBlob } from '@/utils'
import { filterMembers, sortMembers, exportIds } from '@/utils/memberFilters'

export default {
  name: 'MembersView',
  setup() {
    return {
      appStore: useAppStore()
    }
  },
  data() {
    return {
      members: [],
      filters: {
        search: '',
        status: 'ALL',
        paymentStatus: 'ALL',
        joinedFrom: '',
        joinedTo: ''
      },
      sort: { key: null, direction: 'asc' },
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
      const filtered = filterMembers(this.members, this.filters)
      return this.sort.key ? sortMembers(filtered, this.sort.key, this.sort.direction) : filtered
    },
    hasActiveFilters() {
      const f = this.filters
      return !!f.search.trim() || f.status !== 'ALL' || f.paymentStatus !== 'ALL' || !!f.joinedFrom || !!f.joinedTo
    },
    sortIcon() {
      return this.sort.direction === 'asc' ? 'bi bi-caret-up-fill' : 'bi bi-caret-down-fill'
    }
  },
  async created() {
    await this.loadMembers()
  },
  methods: {
    async loadMembers() {
      try {
        const data = await api.getMembers()
        // Ensure members is always an array
        this.members = Array.isArray(data) ? data : []
      } catch (error) {
        console.error('Error loading members:', error)
        // Ensure members is set to empty array on error
        this.members = []
      }
    },
    setSort(key) {
      if (this.sort.key === key) {
        this.sort.direction = this.sort.direction === 'asc' ? 'desc' : 'asc'
      } else {
        this.sort = { key, direction: 'asc' }
      }
    },
    clearFilters() {
      this.filters = { search: '', status: 'ALL', paymentStatus: 'ALL', joinedFrom: '', joinedTo: '' }
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
      new bootstrap.Modal(this.$refs.memberModal).show()
    },
    showEditModal(member) {
      this.editingMember = member
      this.memberForm = { ...member }
      new bootstrap.Modal(this.$refs.memberModal).show()
    },
    showDeleteModal(member) {
      this.selectedMember = member
      new bootstrap.Modal(this.$refs.deleteModal).show()
    },
    async saveMember() {
      try {
        if (this.editingMember) {
          await api.updateMember(this.editingMember.id, this.memberForm)
        } else {
          await api.createMember(this.memberForm)
        }
        await this.loadMembers()
        bootstrap.Modal.getInstance(this.$refs.memberModal).hide()
      } catch (error) {
        console.error('Error saving member:', error)
      }
    },
    async deleteMember() {
      try {
        await api.deleteMember(this.selectedMember.id)
        await this.loadMembers()
        bootstrap.Modal.getInstance(this.$refs.deleteModal).hide()
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
    },
    async exportMembers() {
      if (this.filteredMembers.length === 0) {
        this.appStore.addNotification({
          type: 'warning',
          title: 'Nothing to export',
          message: 'No members match the current filters',
          isToast: true
        })
        return
      }
      try {
        const ids = exportIds(this.filteredMembers, this.members)
        const response = await api.exportMembers(ids)
        // api.request() already returns response.data (the blob)
        const prefix = this.hasActiveFilters ? 'members_filtered' : 'members'
        downloadBlob(response, `${prefix}_${new Date().toISOString().split('T')[0]}.csv`)
      } catch (error) {
        console.error('Error exporting members:', error)
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
.btn-group .btn {
  padding: 0.25rem 0.5rem;
}
.badge {
  font-size: 0.875rem;
}
</style>
