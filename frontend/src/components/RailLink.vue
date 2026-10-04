<template>
  <router-link :to="to" custom v-slot="{ href, navigate, isActive }">
    <a
      :href="href"
      :aria-current="isActive ? 'page' : undefined"
      :class="[
        'group flex items-center gap-2.5 rounded-sm px-2.5 py-1.5 text-base no-underline transition-colors duration-[120ms] hover:bg-teal-tint hover:text-teal',
        isActive ? 'bg-teal-tint font-medium text-teal' : 'text-ink'
      ]"
      @click="navigate"
    >
      <Icon :name="name" :size="18" class="shrink-0" :class="isActive ? 'text-teal' : 'text-muted'" />
      <slot />
    </a>
  </router-link>
</template>

<script>
import Icon from '@/components/Icon.vue'

export default {
  name: 'RailLink',
  components: { Icon },
  props: {
    to: { type: String, required: true },
    // an Icon name; the set lives in components/Icon.vue
    name: { type: String, required: true }
  }
}
</script>
