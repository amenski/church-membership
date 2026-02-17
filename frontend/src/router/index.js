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
    meta: { requiresAuth: true }
  },
  {
    path: '/members',
    name: 'members',
    component: () => import('../views/MembersView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/payments',
    name: 'payments',
    component: () => import('../views/PaymentsView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/communications',
    name: 'communications',
    component: () => import('../views/CommunicationsView.vue'),
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
  if (isAuthenticated && authStore.sessionExpired) {
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
    // If visiting landing or login page, redirect to dashboard
    if (to.path === '/' || to.path === '/login') {
      next('/dashboard')
      return
    }
    const redirectPath = to.query.redirect || '/dashboard'
    next(redirectPath)
    return
  }

  // Check role-based access
  if (to.meta.requiresRole && isAuthenticated) {
    const userRole = authStore.userRole
    const requiredRoles = Array.isArray(to.meta.requiresRole)
      ? to.meta.requiresRole
      : [to.meta.requiresRole]

    if (!requiredRoles.includes(userRole)) {
      next('/?error=access_denied')
      return
    }
  }

  next()
})

export default router
