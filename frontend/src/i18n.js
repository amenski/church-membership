import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    auth: {
      signOut: 'Sign Out'
    }
  }
}

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') || 'en',
  fallbackLocale: 'en',
  messages
})

export default i18n
