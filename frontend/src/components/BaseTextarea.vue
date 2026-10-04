<template>
  <div>
    <label :for="id" class="mb-1 inline-block text-base font-medium text-ink">{{ label }}</label>
    <textarea
      v-bind="$attrs"
      :id="id"
      :value="modelValue"
      :rows="rows"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'block w-full resize-y rounded-md border bg-paper px-3 py-2 text-lg leading-normal text-ink',
        'focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal',
        error ? 'border-clay' : 'border-field'
      ]"
      @input="$emit('update:modelValue', $event.target.value)"
    ></textarea>
    <div class="mt-1 flex justify-between gap-4 text-[0.9375rem]">
      <div class="min-w-0">
        <p v-if="hint" :id="`${id}-hint`" class="m-0 text-muted">{{ hint }}</p>
        <p v-if="error" :id="`${id}-error`" class="m-0 text-clay">{{ error }}</p>
      </div>
      <span v-if="max" :id="`${id}-count`" :class="['shrink-0 tabular-nums', modelValue.length > max ? 'text-clay' : 'text-muted']">{{ modelValue.length }} of {{ max }}</span>
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
