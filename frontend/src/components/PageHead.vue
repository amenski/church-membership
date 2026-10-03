<template>
  <header class="tw:mb-10 tw:flex tw:flex-wrap tw:items-end tw:justify-between tw:gap-x-6 tw:gap-y-4">
    <div>
      <h1 class="tw:m-0 tw:font-display tw:text-2xl tw:leading-[1.2] tw:font-bold tw:text-ink">{{ title }}</h1>
      <p v-if="lead" class="tw:mt-1 tw:mb-0 tw:text-base tw:text-muted">{{ lead }}</p>
    </div>
    <div v-if="$slots.actions" class="tw:flex tw:flex-wrap tw:gap-2"><slot name="actions" /></div>
  </header>
</template>

<script>
export default {
  name: 'PageHead',
  props: {
    title: { type: String, required: true },
    lead: { type: String, default: '' }
  }
}
</script>
