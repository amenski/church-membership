import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

const routes = [
  {
    path: '/',
    name: 'landing',
    component: () => import('../views/LandingView.vue'),
    meta: { requiresGuest: true }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER' }
  },
  {
    path: '/members',
    name: 'members',
    component: () => import('../views/MembersView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER' }
  },
  {
    path: '/payments',
    name: 'payments',
    component: () => import('../views/PaymentsView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER' }
  },
  {
    path: '/communications',
    name: 'communications',
    component: () => import('../views/CommunicationsView.vue'),
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { requiresGuest: true }
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
    next(`${authStore.homePath}?error=access_denied`)
    return
  }

  next()
})

export default router
