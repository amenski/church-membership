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
      title: 'Member Tracker',
      subtitle: 'Manage your organization\'s members, payments, and communications all in one place.',
      signIn: 'Sign In',
      register: 'Register',
      features: 'Features',
      memberManagement: 'Member Management',
      memberManagementDesc: 'Easily add, update, and manage your organization\'s members with detailed profiles.',
      paymentTracking: 'Payment Tracking',
      paymentTrackingDesc: 'Track membership payments, view payment history, and send payment reminders.',
      communications: 'Communications',
      communicationsDesc: 'Send announcements and notifications to all members or specific groups.'
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
