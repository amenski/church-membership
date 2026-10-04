<template>
  <BaseModal :model-value="modelValue" :title="`Archive ${member?.name || 'member'}?`" size="sm" @update:model-value="close">
    <AlertBanner v-if="deleteError">{{ deleteError }}</AlertBanner>
    <p class="m-0 text-base">This hides {{ member?.name || 'the member' }} from the lists. Their payments and messages are kept.</p>
    <template #footer>
      <BaseButton variant="secondary" :disabled="deleting" @click="close(false)">Cancel</BaseButton>
      <BaseButton variant="danger" :disabled="deleting" :aria-busy="deleting ? 'true' : undefined" @click="deleteMember">
        {{ deleting ? 'Archiving...' : 'Archive member' }}
      </BaseButton>
    </template>
  </BaseModal>
</template>

<script>
// The Archive confirmation (ADMIN), shared by the Members list and a member's own page; the screen reloads on `archived`.
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseModal from '@/components/BaseModal.vue'

export default {
  name: 'MemberArchiveDialog',
  components: { AlertBanner, BaseButton, BaseModal },
  props: {
    modelValue: { type: Boolean, default: false },
    member: { type: Object, default: null }
  },
  emits: ['update:modelValue', 'archived'],
  setup() {
    return { appStore: useAppStore() }
  },
  data() {
    return { deleting: false, deleteError: '' }
  },
  watch: {
    modelValue(open) {
      if (open) this.deleteError = ''
    }
  },
  methods: {
    close(open) {
      this.$emit('update:modelValue', open)
    },
    async deleteMember() {
      this.deleting = true
      this.deleteError = ''
      try {
        const { id, name } = this.member
        await api.deleteMember(id)
        this.$emit('archived', { id, name })
        this.close(false)
        this.notify('success', 'Member archived', name)
      } catch (error) {
        console.error('Error archiving member:', error)
        this.deleteError = error.message || 'Request failed'
        // The shared API handler already shows an "Access Denied" toast for 403
        if (error.response?.status !== 403) this.notify('error', 'Could not archive member', error.message || 'Request failed')
      } finally {
        this.deleting = false
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    }
  }
}
</script>
