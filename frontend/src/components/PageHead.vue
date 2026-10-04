<template>
  <header class="mb-10 flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
    <div>
      <h1 class="m-0 font-display text-2xl leading-[1.2] font-bold text-ink">{{ title }}</h1>
      <p v-if="lead" class="mt-1 mb-0 text-base text-muted">{{ lead }}</p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap gap-2"><slot name="actions" /></div>
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
