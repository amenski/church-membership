<template>
  <nav
    aria-label="Main"
    class="fixed inset-x-0 bottom-0 z-[1030] flex border-t border-rule bg-paper pb-[env(safe-area-inset-bottom)] lg:hidden"
  >
    <router-link v-for="tab in TABS" :key="tab.to" :to="tab.to" custom v-slot="{ href, navigate, isActive }">
      <a
        :href="href"
        :aria-current="current(tab, isActive)"
        :class="[
          'flex h-[60px] min-w-0 flex-1 flex-col items-center justify-center gap-0.5 border-t-[3px] text-xs font-medium no-underline transition-colors duration-[120ms]',
          isOn(tab, isActive) ? 'border-teal text-teal' : 'border-transparent text-muted hover:bg-teal-tint hover:text-ink'
        ]"
        @click="navigate"
      >
        <Icon :name="tab.icon" :size="22" />
        {{ tab.label }}
      </a>
    </router-link>
  </nav>
</template>

<script>
import Icon from '@/components/Icon.vue'

// The same five for every role that gets the bar; More holds the rest (see MoreView)
const TABS = [
  { to: '/dashboard', label: 'Overview', icon: 'layout-grid' },
  { to: '/members', label: 'Members', icon: 'users' },
  { to: '/payments', label: 'Payments', icon: 'banknote' },
  { to: '/communications', label: 'Messages', icon: 'message-square' },
  { to: '/more', label: 'More', icon: 'more-horizontal', children: ['/households', '/activity', '/profile'] }
]

export default {
  name: 'BottomTabs',
  components: { Icon },
  data() {
    return { TABS }
  },
  methods: {
    // More also lights up on the pages it opens
    isOn(tab, isActive) {
      return isActive || !!tab.children?.some(path => this.$route.path === path)
    },
    current(tab, isActive) {
      if (isActive) return 'page'
      return this.isOn(tab, isActive) ? 'true' : undefined
    }
  }
}
</script>
