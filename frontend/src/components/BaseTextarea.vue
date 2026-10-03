<template>
  <div>
    <label :for="id" class="tw:mb-1 tw:inline-block tw:text-base tw:font-medium tw:text-ink">{{ label }}</label>
    <textarea
      v-bind="$attrs"
      :id="id"
      :value="modelValue"
      :rows="rows"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'tw:block tw:w-full tw:resize-y tw:rounded-md tw:border tw:bg-paper tw:px-3 tw:py-2 tw:text-lg tw:leading-normal tw:text-ink',
        'tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal',
        error ? 'tw:border-clay' : 'tw:border-field'
      ]"
      @input="$emit('update:modelValue', $event.target.value)"
    ></textarea>
    <div class="tw:mt-1 tw:flex tw:justify-between tw:gap-4 tw:text-[0.9375rem]">
      <div class="tw:min-w-0">
        <p v-if="hint" :id="`${id}-hint`" class="tw:m-0 tw:text-muted">{{ hint }}</p>
        <p v-if="error" :id="`${id}-error`" class="tw:m-0 tw:text-clay">{{ error }}</p>
      </div>
      <span v-if="max" :id="`${id}-count`" :class="['tw:shrink-0 tw:tabular-nums', modelValue.length > max ? 'tw:text-clay' : 'tw:text-muted']">{{ modelValue.length }} of {{ max }}</span>
    </div>
  </div>
</template>

<script>
// A labelled textarea with an optional "12 of 500" counter (max does not stop typing: the form reports it)
export default {
  name: 'BaseTextarea',
  inheritAttrs: false,
  props: {
    id: { type: String, required: true },
    label: { type: String, required: true },
    modelValue: { type: String, default: '' },
    rows: { type: Number, default: 3 },
    max: { type: Number, default: 0 },
    error: { type: String, default: '' },
    hint: { type: String, default: '' }
  },
  emits: ['update:modelValue'],
  computed: {
    describedBy() {
      const ids = []
      if (this.hint) ids.push(`${this.id}-hint`)
      if (this.error) ids.push(`${this.id}-error`)
      if (this.max) ids.push(`${this.id}-count`)
      return ids.length ? ids.join(' ') : undefined
    }
  }
}
</script>
