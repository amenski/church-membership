<template>
  <label class="inline-flex items-center gap-2" :class="tone === 'rail' ? 'text-rail-muted' : 'text-muted'">
    <Icon name="globe" :size="16" class="shrink-0" />
    <span class="sr-only">{{ $t('common.language') }}</span>
    <select
      :value="locale"
      :aria-label="$t('common.language')"
      class="cursor-pointer border-0 bg-transparent p-0 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2"
      :class="tone === 'rail' ? 'text-rail-text focus-visible:outline-paper' : 'text-muted focus-visible:outline-teal'"
      @change="onChange"
    >
      <option v-for="code in SUPPORTED_LOCALES" :key="code" :value="code">{{ $t(`common.languages.${code}`) }}</option>
    </select>
  </label>
</template>

<script setup>
// The UI language, wherever a user can reach it: the rail and the members' top bar (App.vue), the
// profile card, and the sign-in page. Each language names itself, so the control is readable in
// whichever one is showing.
import { useI18n } from 'vue-i18n'
import Icon from '@/components/Icon.vue'
import { SUPPORTED_LOCALES, setLocale } from '@/i18n'
import { useAppStore } from '@/stores/appStore'
import { useAuthStore } from '@/stores/authStore'

defineProps({
  // 'rail' is the dark side rail; 'paper' is the light top bar, the profile card and the sign-in page
  tone: { type: String, default: 'paper' }
})

const { t, locale } = useI18n()
const appStore = useAppStore()
const authStore = useAuthStore()

// The device switches instantly either way; a signed-in user also gets it saved on the account, and
// a failed save goes back to the previous language rather than lying about what was stored.
async function onChange(event) {
  const next = event.target.value
  const previous = locale.value
  if (next === previous) return

  setLocale(next)
  try {
    await authStore.changeLanguage(next)
  } catch {
    setLocale(previous)
    appStore.addNotification({
      type: 'error',
      title: t('common.language'),
      message: t('common.languageSaveFailed'),
      isToast: true
    })
  }
}
</script>
