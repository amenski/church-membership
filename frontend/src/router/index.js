import {createRouter, createWebHistory} from 'vue-router'
import {useAuthStore} from '../stores/authStore'
import Dashboard from '../views/Dashboard.vue'
import MembersView from '../views/MembersView.vue'
import PaymentsView from '../views/PaymentsView.vue'
import CommunicationsView from '../views/CommunicationsView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'

const routes = [
    {
        path: '/',
        name: 'dashboard',
        component: Dashboard,
        meta: { requiresAuth: true }
    },
    {
        path: '/members',
        name: 'members',
        component: MembersView,
        meta: { requiresAuth: true }
    },
    {
        path: '/payments',
        name: 'payments',
        component: PaymentsView,
        meta: { requiresAuth: true }
    },
    {
        path: '/communications',
        name: 'communications',
        component: CommunicationsView,
        meta: { requiresAuth: true }
    },
    {
        path: '/login',
        name: 'login',
        component: LoginView,
        meta: { requiresGuest: true }
    },
    {
        path: '/register',
        name: 'register',
        component: RegisterView,
        meta: { requiresGuest: true }
    },
    // Catch all route for 404
    {
        path: '/:pathMatch(.*)*',
        redirect: '/'
    }
]

const router = createRouter({
    history: createWebHistory(process.env.BASE_URL),
    routes
})

// Navigation guard
router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore()

    // Update user activity for session tracking
    authStore.updateActivity()

    // If auth hasn't been checked yet, wait for it
    if (!authStore.hasAuthChecked) {
        try {
            await authStore.checkAuth()
        } catch (error) {
            // Silent fail - auth check will set isAuthenticated to false
        }
    }

    const isAuthenticated = authStore.isLoggedIn

    // Check if session has expired
    if (isAuthenticated && authStore.sessionExpired) {
        console.warn('Session expired, forcing logout')
        await authStore.logout()
        next('/login?session=expired')
        return
    }

    // Check if route requires authentication
    if (to.meta.requiresAuth && !isAuthenticated) {
        // Store the intended destination for redirect after login
        const redirectPath = to.path !== '/' ? `?redirect=${encodeURIComponent(to.path)}` : ''
        next(`/login${redirectPath}`)
        return
    }

    // Check if route requires guest (non-authenticated)
    if (to.meta.requiresGuest && isAuthenticated) {
        // Check if there's a redirect parameter
        const redirectPath = to.query.redirect || '/'
        next(redirectPath)
        return
    }

    // Check role-based access if route has specific role requirements
    if (to.meta.requiresRole && isAuthenticated) {
        const userRole = authStore.userRole
        const requiredRoles = Array.isArray(to.meta.requiresRole)
            ? to.meta.requiresRole
            : [to.meta.requiresRole]

        if (!requiredRoles.includes(userRole)) {
            console.warn(`Access denied: User role ${userRole} does not have permission for route ${to.path}`)
            next('/?error=access_denied')
            return
        }
    }

    next()
})

export default router
