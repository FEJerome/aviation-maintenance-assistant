import { createApp } from 'vue'
import './style.css'
import 'highlight.js/styles/github.css'
import App from './App.vue'
import { i18n } from './i18n/index.js'

// index.html keeps a static zh-CN <title> as the pre-JS fallback; sync the
// document title from the default locale so a future locale change propagates.
document.title = i18n.global.t('app.title')

createApp(App).use(i18n).mount('#app')
