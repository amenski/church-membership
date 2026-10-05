import { config } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import en from '@/locales/en'
import i18n from '@/i18n'

// The router guard and formatDate read src/i18n.js directly rather than through a mounted component,
// so pin that instance to English too.
i18n.global.locale.value = 'en'

// Every view renders through $t, and every test asserts English copy. Mounting with the real English
// catalog keeps those assertions meaningful and keeps the suites off the app's Amharic default —
// which is why the locale here is 'en' and not what src/i18n.js picks.
export const testI18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: 'en',
  fallbackLocale: 'en',
  missingWarn: false,
  fallbackWarn: false,
  messages: { en }
})

config.global.plugins = [testI18n]
