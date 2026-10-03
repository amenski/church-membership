import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import '@fontsource/alegreya/latin-700.css'
import '@fontsource/alegreya-sans/latin-400.css'
import '@fontsource/alegreya-sans/latin-500.css'
import '@fontsource/alegreya-sans/latin-700.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-700.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap'
import '@/assets/styles/theme.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(i18n)
app.mount('#app')
