import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import MembersView from '../views/MembersView.vue'
import PaymentsView from '../views/PaymentsView.vue'
import CommunicationsView from '../views/CommunicationsView.vue'

const routes = [
    {
        path: '/',
        name: 'dashboard',
        component: Dashboard
    },
    {
        path: '/members',
        name: 'members',
        component: MembersView
    },
    {
        path: '/payments',
        name: 'payments',
        component: PaymentsView
    },
    {
        path: '/communications',
        name: 'communications',
        component: CommunicationsView
    }
]

const router = createRouter({
    history: createWebHistory(process.env.BASE_URL),
    routes
})

export default router