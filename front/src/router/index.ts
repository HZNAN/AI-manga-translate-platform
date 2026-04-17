import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', guest: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', guest: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '漫画书架' },
      },
      {
        path: 'manga/:id',
        name: 'MangaDetail',
        component: () => import('@/views/MangaDetail.vue'),
        meta: { title: '漫画详情' },
      },
      {
        path: 'manga/:id/translate',
        name: 'TranslateManage',
        component: () => import('@/views/TranslateManage.vue'),
        meta: { title: '翻译管理' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '个人设置' },
      },
      {
        path: 'settings/configs',
        name: 'ConfigManage',
        component: () => import('@/views/ConfigManage.vue'),
        meta: { title: '翻译配置管理' },
      },
      {
        path: 'settings/llm-configs',
        name: 'LlmConfigManage',
        component: () => import('@/views/LlmConfigManage.vue'),
        meta: { title: 'LLM 配置管理' },
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('@/views/History.vue'),
        meta: { title: '翻译历史' },
      },
    ],
  },
  {
    path: '/manga/:id/read',
    name: 'Reader',
    component: () => import('@/views/Reader.vue'),
    meta: { title: '阅读器', fullscreen: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const title = (to.meta.title as string) || 'AI漫画翻译'
  document.title = `${title} - AI漫画翻译`
})

export default router
