<template>
  <BaseModal :model-value="modelValue" :title="payment ? $t('receipt.title', { number: receiptNumber(payment) }) : $t('receipt.titlePlain')" size="sm" @update:model-value="close">
    <!-- A plain element for html2pdf to capture: token hex colours only, no tinted or blended colours -->
    <div v-if="payment" ref="receiptContent" class="bg-paper p-2 text-ink">
      <p class="m-0 mb-3 text-base font-semibold">Felege Selam</p>
      <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-base">
        <dt class="font-normal text-muted">{{ $t('receipt.receipt') }}</dt>
        <dd class="m-0 font-medium">{{ receiptNumber(payment) }}</dd>
        <dt class="font-normal text-muted">{{ $t('receipt.member') }}</dt>
        <dd class="m-0 font-medium [overflow-wrap:anywhere]">{{ payment.member?.name || $t('payments.unknown') }}</dd>
        <dt class="font-normal text-muted">{{ $t('receipt.monthCovered') }}</dt>
        <dd class="m-0">{{ periodLabel(payment.period) }}</dd>
        <dt class="font-normal text-muted">{{ $t('receipt.paidOn') }}</dt>
        <dd class="m-0">{{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</dd>
        <dt class="font-normal text-muted">{{ $t('receipt.method') }}</dt>
        <dd class="m-0">{{ $t(methodKey(payment.paymentMethod)) }}</dd>
        <template v-if="payment.notes">
          <dt class="font-normal text-muted">{{ $t('receipt.notes') }}</dt>
          <dd class="m-0 [overflow-wrap:anywhere]">{{ payment.notes }}</dd>
        </template>
      </dl>
      <p class="mt-4 mb-0 border-t border-rule pt-3 text-muted">{{ $t('receipt.amount') }}</p>
      <p :class="[FIGURE, 'm-0 text-2xl']">{{ formatMoney(payment.amount) }}</p>
    </div>
    <template #footer>
      <BaseButton variant="secondary" @click="close(false)">{{ $t('common.close') }}</BaseButton>
      <BaseButton :disabled="downloading" :aria-busy="downloading ? 'true' : undefined" @click="downloadReceipt">
        <Icon name="download" :size="16" class="mr-1.5" />{{ downloading ? $t('receipt.downloading') : $t('receipt.download') }}
      </BaseButton>
    </template>
  </BaseModal>
</template>

<script>
// The receipt for one payment with its PDF download, shared by the Payments screen and a member's own page.
import { useAppStore } from '../stores/appStore'
import { formatDate, formatMoney } from '@/utils'
import { methodKey, periodLabel, receiptNumber } from '@/utils/paymentHistory'
import BaseButton from '@/components/BaseButton.vue'
import BaseModal from '@/components/BaseModal.vue'
import Icon from '@/components/Icon.vue'
import { FIGURE } from '@/ui/classes'

export default {
  name: 'ReceiptDialog',
  components: { BaseButton, BaseModal, Icon },
  props: {
    modelValue: { type: Boolean, default: false },
    payment: { type: Object, default: null }
  },
  emits: ['update:modelValue'],
  setup() {
    return { appStore: useAppStore(), formatDate, formatMoney, methodKey, periodLabel, receiptNumber, FIGURE }
  },
  data() {
    return { downloading: false }
  },
  methods: {
    close(open) {
      this.$emit('update:modelValue', open)
    },
    async downloadReceipt() {
      this.downloading = true
      try {
        const options = {
          margin: 1,
          filename: `receipt-${receiptNumber(this.payment)}.pdf`,
          image: { type: 'jpeg', quality: 0.98 },
          html2canvas: { scale: 2 },
          jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' }
        }
        const { default: html2pdf } = await import('html2pdf.js')
        await html2pdf().set(options).from(this.$refs.receiptContent).save()
      } catch (error) {
        console.error('Error creating the receipt PDF:', error)
        this.appStore.addNotification({ type: 'error', title: this.$t('receipt.createFailed'), message: this.$t('receipt.createFailedMessage'), isToast: true })
      } finally {
        this.downloading = false
      }
    }
  }
}
</script>
