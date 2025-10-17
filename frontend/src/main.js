import { createApp } from 'vue'
import App from './App.vue'
import { initializeStores } from './stores'

import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'

import '@/assets/styles/bootstrap-theme.css'

const app = createApp(App)

// Initialize Pinia and stores BEFORE importing router
// This ensures stores are available when router navigation guards execute
initializeStores(app)

// Import router AFTER Pinia is set up
// This is a dynamic import to ensure proper initialization order
const { default: router } = await import('./router')

app.use(router)
app.mount('#app')
