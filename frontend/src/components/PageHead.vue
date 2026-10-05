<template>
  <header :class="['mb-6 flex flex-wrap items-end justify-between gap-x-4 gap-y-3 border-b border-rule pb-4', compact ? 'max-lg:mb-3 max-lg:items-center max-lg:pb-3' : '', band ? 'lg:mb-0 lg:bg-paper lg:pb-0' : '']">
    <!-- a band centres its content at the page width (a pass-through box otherwise) -->
    <div :class="band ? 'contents lg:mx-auto lg:flex lg:min-h-16 lg:w-full lg:max-w-[1400px] lg:flex-wrap lg:items-center lg:justify-between lg:gap-x-4 lg:gap-y-3 lg:px-8' : 'contents'">
      <div>
        <h1 class="m-0 text-2xl font-semibold text-ink">{{ title }}</h1>
        <p v-if="lead" :class="['mt-0.5 mb-0 text-sm text-muted', compact ? 'max-lg:hidden' : '', band ? 'lg:hidden' : '']">{{ lead }}</p>
        <div v-if="$slots.meta" class="mt-1"><slot name="meta" /></div>
      </div>
      <div v-if="$slots.actions" class="flex flex-wrap gap-2"><slot name="actions" /></div>
    </div>
  </header>
</template>

<script>
export default {
  name: 'PageHead',
  props: {
    title: { type: String, required: true },
    lead: { type: String, default: '' },
    // a phone header: no lead, tighter spacing, the action centred on the title (below lg only, to match the stacked cards)
    compact: { type: Boolean, default: false },
    // from lg a full-width white band (64px, rule under it, no lead) instead of the rule under the title; the screen
    // puts its content in its own padded area below it. Below lg it is the normal header.
    band: { type: Boolean, default: false }
  }
}
</script>
