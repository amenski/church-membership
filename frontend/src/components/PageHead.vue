<template>
  <header class="tw:mb-10">
    <h1 class="tw:m-0 tw:font-display tw:text-2xl tw:leading-[1.2] tw:font-bold tw:text-ink">{{ title }}</h1>
    <p v-if="lead" class="tw:mt-1 tw:mb-0 tw:text-base tw:text-muted">{{ lead }}</p>
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
