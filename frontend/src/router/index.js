import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import i18n from '../i18n'

// `titleKey` is the i18n key of the page name in the top bar's breadcrumb; the guards read the rest.
const routes = [
  // There is no landing page: the root is a doorway to sign-in, and the guard sends an
  // already-signed-in visitor on to their home screen.
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.overview' }
  },
  {
    path: '/members',
    name: 'members',
    component: () => import('../views/MembersView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.members' }
  },
  {
    path: '/members/:id',
    name: 'member',
    component: () => import('../views/MemberDetailView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.member' }
  },
  {
    path: '/households',
    name: 'households',
    component: () => import('../views/HouseholdsView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.households' }
  },
  {
    path: '/payments',
    name: 'payments',
    component: () => import('../views/PaymentsView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.payments' }
  },
  {
    path: '/communications',
    name: 'communications',
    component: () => import('../views/CommunicationsView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.messages' }
  },
  {
    path: '/activity',
    name: 'activity',
    component: () => import('../views/ActivityView.vue'),
    meta: { requiresAuth: true, requiresRole: 'ADMIN', titleKey: 'nav.activity' }
  },
  {
    path: '/more',
    name: 'more',
    component: () => import('../views/MoreView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER', titleKey: 'nav.more' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true, titleKey: 'nav.profile' }
  },
  {
    path: '/my-dues',
    name: 'my-dues',
    component: () => import('../views/MyDuesView.vue'),
    meta: { requiresAuth: true, titleKey: 'nav.myDues' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { requiresGuest: true, titleKey: 'nav.signIn' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return { top: 0 }
  }
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // Initialize auth when needed (only runs once due to authChecked guard)
  try {
    await authStore.checkAuth()
  } catch {
    // Swallow — checkAuth already handles errors internally
  }

  const isAuthenticated = authStore.isLoggedIn

  // Check if session expired
  if (isAuthenticated && authStore.isSessionExpired()) {
    await authStore.logout()
    next('/login?session=expired')
    return
  }

  // Check route requires auth
  if (to.meta.requiresAuth && !isAuthenticated) {
    const redirectPath = to.path !== '/' && to.path !== '/dashboard'
      ? `?redirect=${encodeURIComponent(to.path)}`
      : ''
    next(`/login${redirectPath}`)
    return
  }

  // Check route requires guest (non-authenticated)
  if (to.meta.requiresGuest && isAuthenticated) {
    // If visiting landing or login page, redirect to the user's home page
    if (to.path === '/' || to.path === '/login') {
      next(authStore.homePath)
      return
    }
    const redirectPath = to.query.redirect || authStore.homePath
    next(redirectPath)
    return
  }

  // Check role-based access (requiresRole is a minimum role)
  if (to.meta.requiresRole && isAuthenticated && !authStore.hasRole(to.meta.requiresRole)) {
    useAppStore().addNotification({
      type: 'warning',
      title: i18n.global.t('errors.accessDeniedTitle'),
      message: i18n.global.t('errors.accessDeniedMessage'),
      duration: 5000
    })
    next(authStore.homePath)
    return
  }

  next()
})

export default router
