import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-700.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
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
