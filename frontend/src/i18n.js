import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    app: {
      title: 'Member Tracker'
    },
    nav: {
      dashboard: 'Dashboard',
      members: 'Members',
      payments: 'Payments',
      communications: 'Communications',
      signOut: 'Sign Out'
    },
    auth: {
      signInAs: 'Signed in as',
      login: 'Login',
      logout: 'Logout',
      signOut: 'Sign Out'
    },
    landing: {
      title: 'Felege Selam',
      tagline: 'Membership and dues for the church community.',
      signIn: 'Sign in',
      note: 'Accounts are set up by the church office.'
    },
    common: {
      save: 'Save',
      cancel: 'Cancel',
      delete: 'Delete',
      edit: 'Edit',
      add: 'Add',
      search: 'Search',
      loading: 'Loading...',
      noData: 'No data available',
      success: 'Success',
      error: 'Error',
      warning: 'Warning',
      confirm: 'Confirm'
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
