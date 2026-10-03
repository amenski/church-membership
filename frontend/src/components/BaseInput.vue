<template>
  <div>
    <label :for="id" class="tw:mb-1 tw:inline-block tw:text-base tw:font-medium tw:text-ink">{{ label }}</label>
    <input
      v-bind="$attrs"
      :id="id"
      :type="type"
      :value="modelValue"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="error ? `${id}-error` : undefined"
      :class="[
        'tw:block tw:w-full tw:rounded-md tw:border tw:bg-paper tw:px-3 tw:py-2 tw:text-lg tw:leading-normal tw:text-ink',
        'tw:placeholder:text-muted tw:placeholder:opacity-80',
        'tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal',
        'tw:disabled:bg-mist tw:disabled:text-muted',
        error ? 'tw:border-clay' : 'tw:border-field'
      ]"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <p v-if="error" :id="`${id}-error`" class="tw:mt-1 tw:mb-0 tw:text-[0.9375rem] tw:text-clay">{{ error }}</p>
  </div>
</template>

<script>
export default {
  name: 'BaseInput',
  inheritAttrs: false,
  props: {
    id: { type: String, required: true },
    label: { type: String, required: true },
    modelValue: { type: String, default: '' },
    type: { type: String, default: 'text' },
    error: { type: String, default: '' },
    disabled: { type: Boolean, default: false }
  },
  emits: ['update:modelValue']
}
</script>
