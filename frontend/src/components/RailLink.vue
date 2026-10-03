<template>
  <router-link :to="to" custom v-slot="{ href, navigate, isActive }">
    <a
      :href="href"
      :aria-current="isActive ? 'page' : undefined"
      :class="[
        'tw:group tw:relative tw:flex tw:items-center tw:gap-3 tw:px-6 tw:py-[10px] tw:text-lg tw:no-underline tw:transition-colors tw:duration-[120ms] tw:hover:text-teal',
        isActive ? 'tw:font-bold tw:text-teal' : 'tw:font-medium tw:text-ink'
      ]"
      @click="navigate"
    >
      <span v-if="isActive" class="tw:absolute tw:inset-y-[6px] tw:left-0 tw:w-[3px] tw:bg-teal" aria-hidden="true"></span>
      <i
        :class="['bi', icon, 'tw:text-lg tw:transition-colors tw:duration-[120ms] tw:group-hover:text-teal', isActive ? 'tw:text-teal' : 'tw:text-muted']"
        aria-hidden="true"
      ></i>
      <slot />
    </a>
  </router-link>
</template>

<script>
export default {
  name: 'RailLink',
  props: {
    to: { type: String, required: true },
    icon: { type: String, required: true }
  }
}
</script>
