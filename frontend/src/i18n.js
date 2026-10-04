import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    auth: {
      signOut: 'Sign Out'
    },
    landing: {
      title: 'Felege Selam',
      tagline: 'Membership and dues for the church community.',
      signIn: 'Sign in',
      note: 'Accounts are set up by the church office.'
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
