import { createI18n } from 'vue-i18n'
import en from '@/locales/en'
import am from '@/locales/am'

export const SUPPORTED_LOCALES = ['am', 'en']

// Amharic first: it is the language most of the congregation reads. English is the fallback for any
// key am.js has not translated yet, so a partial catalog degrades to English, never to a raw key.
export const DEFAULT_LOCALE = 'am'

const stored = () => {
  try {
    return localStorage.getItem('locale')
  } catch {
    return null
  }
}

const i18n = createI18n({
  legacy: false,
  // Every template can use $t without importing useI18n
  globalInjection: true,
  locale: SUPPORTED_LOCALES.includes(stored()) ? stored() : DEFAULT_LOCALE,
  fallbackLocale: 'en',
  messages: { en, am }
})

/**
 * Switch the UI language and remember the choice on this device.
 *
 * Deliberately does not touch the API: services/api.js already imports the auth store, so reaching
 * back from here would make an import cycle. The component that offers the switch is the one that
 * saves it to the account.
 *
 * @param {string} locale - one of SUPPORTED_LOCALES; anything else is ignored
 */
export function setLocale(locale) {
  if (!SUPPORTED_LOCALES.includes(locale)) return
  i18n.global.locale.value = locale
  try {
    localStorage.setItem('locale', locale)
  } catch {
    // A private window with storage blocked still gets the language for this page
  }
  document.documentElement.lang = locale
}

export default i18n
