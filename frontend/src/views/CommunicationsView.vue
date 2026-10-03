<template>
  <div>
    <PageHead title="Messages" lead="Email members and see what was delivered." />

    <AlertBanner v-if="loadError">
      <div class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
        <span>The messages did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadData">Try again</BaseButton>
      </div>
    </AlertBanner>

    <!-- One reading column for the form and the history -->
    <div class="tw:max-w-176">
      <!-- Compose (STAFF and above) -->
      <section v-if="authStore.isStaff" :class="CARD" aria-labelledby="compose-title">
        <SectionTitle id="compose-title">New message</SectionTitle>
        <AlertBanner v-if="sendError">{{ sendError }}</AlertBanner>
        <form class="tw:flex tw:flex-col tw:gap-4" novalidate @submit.prevent="askToSend">
          <div class="tw:grid tw:grid-cols-1 tw:gap-4 tw:sm:grid-cols-2">
            <BaseSelect id="message-audience" v-model="form.recipientType" label="Send to">
              <option value="ALL">All active members</option>
              <option value="OVERDUE">Members behind on dues</option>
              <option value="SPECIFIC">One member</option>
            </BaseSelect>
            <BaseInput
              v-if="form.recipientType === 'OVERDUE'"
              id="message-months"
              v-model="form.monthsOverdue"
              label="At least this many months behind"
              type="number"
              min="1"
              step="1"
              inputmode="numeric"
              :error="formErrors.monthsOverdue"
            />
            <BaseSelect v-else-if="form.recipientType === 'SPECIFIC'" id="message-member" v-model="form.memberId" label="Member" :error="formErrors.memberId">
              <option value="" disabled>Choose a member</option>
              <option v-for="member in activeMembers" :key="member.id" :value="String(member.id)">{{ member.name }}</option>
            </BaseSelect>
          </div>
          <BaseInput id="message-subject" v-model="form.subject" label="Subject" maxlength="200" autocomplete="off" :error="formErrors.subject" />
          <BaseTextarea
            id="message-body"
            v-model="form.message"
            label="Message"
            :rows="6"
            :max="MESSAGE_MAX"
            hint="Write {{member_name}} to insert each member's name."
            :error="formErrors.message"
          />
          <div>
            <BaseButton type="submit" :disabled="sending" :aria-busy="sending ? 'true' : undefined" class="tw:max-sm:w-full">
              {{ sending ? 'Sending...' : 'Send message' }}
            </BaseButton>
          </div>
        </form>
      </section>

      <!-- History -->
      <section aria-labelledby="history-title">
        <SectionTitle id="history-title">Sent messages</SectionTitle>

        <p v-if="!loaded" class="tw:m-0 tw:py-4 tw:text-(length:--text-body) tw:text-muted" role="status">Loading messages...</p>

        <template v-else-if="!loadError">
          <div v-if="!messages.length">
            <EmptyNote>No messages yet.<template v-if="authStore.isStaff"> Use the form above to send the first one.</template></EmptyNote>
          </div>

          <ul v-else class="tw:m-0 tw:list-none tw:border-t tw:border-rule tw:p-0">
            <li v-for="message in messages" :key="message.id" class="tw:flex tw:items-start tw:justify-between tw:gap-4 tw:border-b tw:border-rule tw:py-3 tw:text-(length:--text-body) tw:max-sm:flex-col tw:max-sm:gap-0">
              <div class="tw:min-w-0">
                <div class="tw:font-sans tw:font-bold tw:text-ink tw:[overflow-wrap:anywhere]">{{ message.title }}</div>
                <div class="tw:text-sm tw:text-muted tw:tabular-nums">
                  {{ sentAt(message) }} &middot; {{ typeLabel(message.type) }} &middot;
                  <template v-if="message.recipientCount > 0">{{ message.recipientCount }} {{ message.recipientCount === 1 ? 'recipient' : 'recipients' }}</template>
                  <template v-else>No deliveries recorded</template>
                </div>
                <div v-if="summaryParts(message).length" class="tw:mt-1 tw:flex tw:flex-wrap tw:gap-x-4">
                  <StatusLabel v-for="part in summaryParts(message)" :key="part.key" :tone="part.tone">{{ part.text }}</StatusLabel>
                </div>
              </div>
              <TextButton class="tw:shrink-0 tw:max-sm:text-left" @click="openDeliveries(message)">
                View deliveries<span class="tw:sr-only"> for {{ message.title }}</span>
              </TextButton>
            </li>
          </ul>
        </template>
      </section>
    </div>

    <!-- Confirm before email goes out -->
    <ConfirmDialog
      v-model="confirmOpen"
      :title="`Send to ${countLabel}?`"
      :message="`“${form.subject.trim()}” goes out by email. You cannot take it back.`"
      :confirm-label="`Send to ${countLabel}`"
      :busy="sending"
      @confirm="sendMessage"
    />

    <!-- Deliveries -->
    <BaseModal v-model="deliveriesOpen" :title="selectedMessage ? `Deliveries: ${selectedMessage.title}` : 'Deliveries'" size="lg">
      <p v-if="selectedMessage" class="tw:mt-0 tw:mb-3 tw:text-base tw:text-muted tw:tabular-nums">Sent {{ sentAt(selectedMessage) }}</p>
      <p v-if="deliveriesLoading" class="tw:m-0 tw:py-4 tw:text-muted" role="status">Loading deliveries...</p>
      <AlertBanner v-else-if="deliveriesError">
        <div class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
          <span>The deliveries did not load. Check your connection and try again.</span>
          <BaseButton variant="secondary" size="sm" @click="loadDeliveries">Try again</BaseButton>
        </div>
      </AlertBanner>
      <EmptyNote v-else-if="!deliveries.length">No deliveries were recorded for this message.</EmptyNote>
      <template v-else>
        <div class="tw:mb-3 tw:flex tw:flex-wrap tw:gap-x-4" aria-label="Delivery totals" role="group">
          <StatusLabel v-for="part in deliveryTotals" :key="part.key" :tone="part.tone">{{ part.text }}</StatusLabel>
        </div>
        <ul class="tw:m-0 tw:list-none tw:border-t tw:border-rule tw:p-0">
          <li v-for="delivery in deliveries" :key="delivery.id" class="tw:flex tw:items-start tw:justify-between tw:gap-3 tw:border-b tw:border-rule tw:py-3">
            <div class="tw:min-w-0">
              <div class="tw:flex tw:flex-wrap tw:items-baseline tw:gap-x-4">
                <span class="tw:font-medium tw:[overflow-wrap:anywhere]">{{ delivery.recipient?.name || 'Unknown' }}</span>
                <StatusLabel :tone="statusOf(delivery).tone">{{ statusOf(delivery).label }}</StatusLabel>
                <span v-if="delivery.deliveryTime" class="tw:text-sm tw:text-muted tw:tabular-nums">{{ formatDate(delivery.deliveryTime, 'MMM d, yyyy, h:mm a') }}</span>
              </div>
              <div class="tw:text-sm tw:text-muted tw:[overflow-wrap:anywhere]">{{ delivery.recipient?.email }}</div>
              <div v-if="delivery.responseNotes" class="tw:text-sm tw:text-muted tw:[overflow-wrap:anywhere]">{{ delivery.responseNotes }}</div>
            </div>
            <BaseButton
              v-if="canRetry(delivery)"
              variant="secondary"
              size="sm"
              class="tw:shrink-0"
              :disabled="retryingIds.includes(delivery.id)"
              :aria-busy="retryingIds.includes(delivery.id) ? 'true' : undefined"
              @click="retryDelivery(delivery)"
            >
              {{ retryingIds.includes(delivery.id) ? 'Retrying...' : 'Retry' }}<span class="tw:sr-only"> for {{ delivery.recipient?.name }}</span>
            </BaseButton>
          </li>
        </ul>
      </template>
      <template #footer>
        <BaseButton variant="secondary" @click="deliveriesOpen = false">Close</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { formatDate } from '@/utils'
import { audienceCount } from '@/utils/audienceCount'
import { buildCommunicationRequest } from '@/utils/communicationPayload'
import { countDeliveries, deliveryStatus, deliverySummaryParts, sortMessages, typeLabel } from '@/utils/messageHistory'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'

const CARD = 'tw:mb-10 tw:rounded-md tw:border tw:border-rule tw:bg-paper tw:p-(--card-pad)'
const MESSAGE_MAX = 5000
const SUBJECT_MAX = 200
const EMPTY_ERRORS = { monthsOverdue: '', memberId: '', subject: '', message: '' }
// The server names the request fields title and messageContent; the form calls them subject and message
const SERVER_FIELDS = { title: 'subject', messageContent: 'message' }

const emptyForm = () => ({ recipientType: 'ALL', memberId: '', monthsOverdue: '1', subject: '', message: '' })
const plural = (n) => `${n} ${n === 1 ? 'member' : 'members'}`

export default {
  name: 'CommunicationsView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, ConfirmDialog, EmptyNote, PageHead, SectionTitle, StatusLabel, TextButton },
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      formatDate,
      typeLabel,
      CARD,
      MESSAGE_MAX
    }
  },
  data() {
    return {
      members: [],
      communications: [],
      loaded: false,
      loadError: false,
      form: emptyForm(),
      formErrors: { ...EMPTY_ERRORS },
      sendError: '',
      sending: false,
      confirmOpen: false,
      selectedMessage: null,
      deliveriesOpen: false,
      deliveries: [],
      deliveriesLoading: false,
      deliveriesError: false,
      retryingIds: []
    }
  },
  computed: {
    activeMembers() {
      return this.members.filter(member => member.active).sort((a, b) => a.name.localeCompare(b.name))
    },
    messages() {
      return sortMessages(this.communications)
    },
    recipientTotal() {
      return audienceCount(this.members, this.form.recipientType, this.form.monthsOverdue)
    },
    countLabel() {
      return plural(this.recipientTotal)
    },
    deliveryTotals() {
      return deliverySummaryParts(countDeliveries(this.deliveries))
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    // Members are only needed to compose and count recipients (STAFF and above)
    async loadData() {
      try {
        const [members, communications] = await Promise.all([
          this.authStore.isStaff ? api.getMembers() : [],
          api.getCommunications()
        ])
        this.members = Array.isArray(members) ? members : []
        this.communications = Array.isArray(communications) ? communications : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading messages:', error)
        this.members = []
        this.communications = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    async refreshMessages() {
      try {
        const communications = await api.getCommunications()
        this.communications = Array.isArray(communications) ? communications : []
      } catch (error) {
        console.error('Error refreshing messages:', error)
      }
    },
    sentAt(message) {
      return formatDate(message.sentDate || message.createdDate, 'MMM d, yyyy, h:mm a')
    },
    summaryParts(message) {
      return deliverySummaryParts(message.deliverySummary)
    },
    statusOf(delivery) {
      return deliveryStatus(delivery.status)
    },
    validateForm() {
      const errors = { ...EMPTY_ERRORS }
      const f = this.form
      if (f.recipientType === 'OVERDUE' && !(Number(f.monthsOverdue) >= 1)) errors.monthsOverdue = 'Enter 1 or more months.'
      if (f.recipientType === 'SPECIFIC' && !f.memberId) errors.memberId = 'Choose a member.'
      if (!f.subject.trim()) errors.subject = 'Enter a subject.'
      else if (f.subject.trim().length > SUBJECT_MAX) errors.subject = `The subject can be up to ${SUBJECT_MAX} characters.`
      if (!f.message.trim()) errors.message = 'Write a message.'
      else if (f.message.trim().length > MESSAGE_MAX) errors.message = `The message can be up to ${MESSAGE_MAX} characters.`
      this.formErrors = errors
      return !Object.values(errors).some(Boolean)
    },
    // Check the form, then ask before email goes out
    askToSend() {
      this.sendError = ''
      if (!this.validateForm()) return
      if (this.recipientTotal === 0) {
        this.sendError = 'There is nobody to send this to.'
        return
      }
      this.confirmOpen = true
    },
    async sendMessage() {
      const payload = buildCommunicationRequest(this.form)
      const { recipientType, memberId, monthsOverdue } = this.form
      const count = this.recipientTotal
      this.sending = true
      try {
        if (recipientType === 'ALL') await api.sendToAllMembers(payload)
        else if (recipientType === 'OVERDUE') await api.sendToOverdueMembers(Number(monthsOverdue), payload)
        else await api.sendToMember(memberId, payload)
        this.confirmOpen = false
        this.form = emptyForm()
        this.formErrors = { ...EMPTY_ERRORS }
        this.notify('success', 'Sending started', `${plural(count)} will get it in the next few minutes. Check the delivery status below.`)
        await this.refreshMessages()
      } catch (error) {
        console.error('Error sending message:', error)
        this.confirmOpen = false
        this.showSendError(error)
      } finally {
        this.sending = false
      }
    },
    // A field error goes under its field; anything else goes in the banner of the card
    showSendError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        const key = SERVER_FIELDS[field] || field
        if (key in EMPTY_ERRORS && !this.formErrors[key]) this.formErrors[key] = message
        else rest.push(message)
      }
      if (!fieldErrors.length) rest.push(error.message || 'The message was not sent. Try again.')
      if (rest.length) {
        this.sendError = rest.join(' ')
        this.notifyFailure('Could not send message', error)
      }
    },
    async openDeliveries(message) {
      this.selectedMessage = message
      this.deliveries = []
      this.deliveriesOpen = true
      await this.loadDeliveries()
    },
    async loadDeliveries() {
      this.deliveriesLoading = true
      this.deliveriesError = false
      try {
        const deliveries = await api.getCommunicationDeliveries(this.selectedMessage.id)
        this.deliveries = Array.isArray(deliveries) ? deliveries : []
      } catch (error) {
        console.error('Error loading deliveries:', error)
        this.deliveries = []
        this.deliveriesError = true
      } finally {
        this.deliveriesLoading = false
      }
    },
    // Only a failed email can be sent again, and only by STAFF and above
    canRetry(delivery) {
      return this.authStore.isStaff && delivery.status === 'FAILED' && delivery.channel === 'EMAIL'
    },
    async retryDelivery(delivery) {
      this.retryingIds.push(delivery.id)
      try {
        const updated = await api.retryDelivery(this.selectedMessage.id, delivery.id)
        this.deliveries = this.deliveries.map(item => (item.id === updated.id ? updated : item))
        if (updated.status === 'FAILED') this.notify('warning', 'Retry failed', `The email to ${delivery.recipient?.name || 'the member'} still did not go out. Check the address and try again later.`)
        else this.notify('success', 'Delivery retried', delivery.recipient?.name || '')
        await this.refreshMessages()
      } catch (error) {
        console.error('Error retrying delivery:', error)
        this.notifyFailure('Could not retry delivery', error)
      } finally {
        this.retryingIds = this.retryingIds.filter(id => id !== delivery.id)
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || 'Request failed')
    }
  }
}
</script>
