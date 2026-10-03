import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Global styles first so component styles in App.vue can override Bootstrap
import '@fontsource/alegreya/latin-400.css'
import '@fontsource/alegreya/latin-700.css'
import '@fontsource/alegreya-sans/latin-400.css'
import '@fontsource/alegreya-sans/latin-500.css'
import '@fontsource/alegreya-sans/latin-700.css'
import '@fontsource/noto-sans-ethiopic/ethiopic-700.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import 'bootstrap'
// Bootstrap's CSS and theme.css are imported inside tailwind.css, into layers below
// Tailwind's utilities (see the layer note there). Phase T4 removes them.
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
