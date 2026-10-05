<template>
  <BaseModal :model-value="modelValue" :title="title" size="sm" @update:model-value="$emit('update:modelValue', $event)">
    <p class="m-0 text-base">{{ message }}</p>
    <template #footer>
      <BaseButton ref="cancel" variant="secondary" :disabled="busy" @click="$emit('update:modelValue', false)">{{ $t('common.cancel') }}</BaseButton>
      <BaseButton :variant="danger ? 'danger' : 'primary'" :disabled="busy" :aria-busy="busy ? 'true' : undefined" @click="$emit('confirm')">
        {{ confirmLabel }}
      </BaseButton>
    </template>
  </BaseModal>
</template>

<script>
import BaseButton from '@/components/BaseButton.vue'
import BaseModal from '@/components/BaseModal.vue'

// "Are you sure?" for an action that cannot be taken back, such as sending email. The parent owns
// the work: it closes the dialog (v-model) when the work is done and shows `busy` while it runs.
// Opening puts focus on Cancel, so a stray Enter never confirms.
export default {
  name: 'ConfirmDialog',
  components: { BaseButton, BaseModal },
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, required: true },
    message: { type: String, default: '' },
    confirmLabel: { type: String, required: true },
    danger: { type: Boolean, default: false },
    busy: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'confirm'],
  watch: {
    async modelValue(open) {
      if (!open) return
      // BaseModal focuses its first control on the next tick; wait for that, then take focus
      await this.$nextTick()
      await this.$nextTick()
      this.$refs.cancel?.$el.focus()
    }
  }
}
</script>
