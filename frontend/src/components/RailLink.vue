<template>
  <router-link :to="to" custom v-slot="{ href, navigate, isActive }">
    <a
      :href="href"
      :aria-current="isActive ? 'page' : undefined"
      :class="[
        'group flex items-center gap-2.5 rounded-md px-2.5 py-2 text-base font-medium no-underline transition-colors duration-[120ms] focus-visible:outline-paper',
        isActive ? 'bg-teal text-paper' : 'text-rail-text hover:bg-rail-hover hover:text-paper'
      ]"
      @click="navigate"
    >
      <Icon :name="name" :size="18" class="shrink-0" :class="isActive ? 'text-paper' : 'text-rail-muted group-hover:text-paper'" />
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
