<template>
  <div>
    <label :for="id" class="tw:mb-1 tw:inline-block tw:text-base tw:font-medium tw:text-ink">{{ label }}</label>
    <select
      v-bind="$attrs"
      :id="id"
      :value="modelValue"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'tw:block tw:w-full tw:rounded-md tw:border tw:bg-paper tw:px-3 tw:py-2 tw:text-lg tw:leading-normal tw:text-ink',
        'tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal',
        error ? 'tw:border-clay' : 'tw:border-field'
      ]"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <slot />
    </select>
    <p v-if="hint" :id="`${id}-hint`" class="tw:mt-1 tw:mb-0 tw:text-[0.9375rem] tw:text-muted">{{ hint }}</p>
    <p v-if="error" :id="`${id}-error`" class="tw:mt-1 tw:mb-0 tw:text-[0.9375rem] tw:text-clay">{{ error }}</p>
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
