<template>
  <div>
    <label :for="id" class="mb-1 inline-block text-base font-medium text-ink">{{ label }}</label>
    <select
      v-bind="$attrs"
      :id="id"
      :value="modelValue"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'block w-full rounded-md border bg-paper px-3 py-2 text-lg leading-normal text-ink',
        'focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal',
        error ? 'border-clay' : 'border-field'
      ]"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <slot />
    </select>
    <p v-if="hint" :id="`${id}-hint`" class="mt-1 mb-0 text-[0.9375rem] text-muted">{{ hint }}</p>
    <p v-if="error" :id="`${id}-error`" class="mt-1 mb-0 text-[0.9375rem] text-clay">{{ error }}</p>
  </div>
</template>

<script>
// A labelled select for dialogs (same look as BaseInput); options come in the default slot
export default {
  name: 'BaseSelect',
  inheritAttrs: false,
  props: {
    id: { type: String, required: true },
    label: { type: String, required: true },
    modelValue: { type: [String, Number], default: '' },
    error: { type: String, default: '' },
    hint: { type: String, default: '' }
  },
  emits: ['update:modelValue'],
  computed: {
    describedBy() {
      const ids = []
      if (this.hint) ids.push(`${this.id}-hint`)
      if (this.error) ids.push(`${this.id}-error`)
      return ids.length ? ids.join(' ') : undefined
    }
  }
}
</script>
