import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-400.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-500.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-600.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-700.css'
import '@/assets/styles/tailwind.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(i18n)
app.mount('#app')
