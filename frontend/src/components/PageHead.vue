<template>
  <header :class="['mb-6 flex flex-wrap items-end justify-between gap-x-4 gap-y-3 border-b border-rule pb-4', compact ? 'max-md:mb-3 max-md:items-center max-md:pb-3' : '']">
    <div>
      <h1 class="m-0 text-2xl font-semibold text-ink">{{ title }}</h1>
      <p v-if="lead" :class="['mt-0.5 mb-0 text-sm text-muted', compact ? 'max-md:hidden' : '']">{{ lead }}</p>
      <div v-if="$slots.meta" class="mt-1"><slot name="meta" /></div>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap gap-2"><slot name="actions" /></div>
  </header>
</template>

<script>
export default {
  name: 'PageHead',
  props: {
    title: { type: String, required: true },
    lead: { type: String, default: '' },
    // a phone header: no lead, tighter spacing, the action centred on the title (below md only)
    compact: { type: Boolean, default: false }
  }
}
</script>
