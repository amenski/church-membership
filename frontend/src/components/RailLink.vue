<template>
  <router-link :to="to" custom v-slot="{ href, navigate, isActive }">
    <a
      :href="href"
      :aria-current="isActive ? 'page' : undefined"
      :class="[
        'group relative flex items-center gap-3 px-6 py-[10px] text-lg no-underline transition-colors duration-[120ms] hover:text-teal',
        isActive ? 'font-bold text-teal' : 'font-medium text-ink'
      ]"
      @click="navigate"
    >
      <span v-if="isActive" class="absolute inset-y-[6px] left-0 w-[3px] bg-teal" aria-hidden="true"></span>
      <i
        :class="['bi', icon, 'text-lg transition-colors duration-[120ms] group-hover:text-teal', isActive ? 'text-teal' : 'text-muted']"
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
