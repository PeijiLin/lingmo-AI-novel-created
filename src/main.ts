import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
// ✅ 引入并初始化拦截器
import '@/api/axiosInstance.ts';

const pinia = createPinia();
pinia.use(piniaPluginPersistedstate)
const app = createApp(App)
app.use(pinia)
app.use(router)
app.mount('#app')
