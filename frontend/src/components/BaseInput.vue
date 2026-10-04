<template>
  <div>
    <label :for="id" class="mb-1 block text-xs font-medium text-muted">{{ label }}</label>
    <input
      v-bind="$attrs"
      :id="id"
      :type="type"
      :value="modelValue"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'block w-full rounded-sm border bg-paper px-2.5 py-1.5 text-lg text-ink',
        'placeholder:text-muted',
        'focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal',
        'disabled:bg-mist disabled:text-muted',
        error ? 'border-clay' : 'border-field'
      ]"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <p v-if="dateText" :id="`${id}-date`" class="mt-1 mb-0 text-xs text-muted">{{ dateText }}</p>
    <p v-if="hint" :id="`${id}-hint`" class="mt-1 mb-0 text-xs text-muted">{{ hint }}</p>
    <p v-if="error" :id="`${id}-error`" class="mt-1 mb-0 text-xs text-clay">{{ error }}</p>
  </div>
</template>

<script>
import { formatDate } from '@/utils'

export default {
  name: 'BaseInput',
  inheritAttrs: false,
  props: {
    id: { type: String, required: true },
    label: { type: String, required: true },
    modelValue: { type: String, default: '' },
    type: { type: String, default: 'text' },
    error: { type: String, default: '' },
    // quiet help under the field ("Optional. 10 digits or more.")
    hint: { type: String, default: '' },
    disabled: { type: Boolean, default: false }
  },
  emits: ['update:modelValue'],
  computed: {
    // the native date input shows the browser's locale (dd/mm/yyyy); echo the chosen day the way the app prints dates
    dateText() {
      return this.type === 'date' && this.modelValue ? formatDate(this.modelValue, 'MMM d, yyyy') : ''
    },
    describedBy() {
      const ids = []
      if (this.dateText) ids.push(`${this.id}-date`)
      if (this.hint) ids.push(`${this.id}-hint`)
      if (this.error) ids.push(`${this.id}-error`)
      return ids.length ? ids.join(' ') : undefined
    }
  }
}
</script>
