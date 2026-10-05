import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import Dashboard from '@/views/Dashboard.vue'
import MembersView from '@/views/MembersView.vue'
import MemberDetailView from '@/views/MemberDetailView.vue'
import HouseholdsView from '@/views/HouseholdsView.vue'
import PaymentsView from '@/views/PaymentsView.vue'
import CommunicationsView from '@/views/CommunicationsView.vue'
import ActivityView from '@/views/ActivityView.vue'
import MoreView from '@/views/MoreView.vue'
import ProfileView from '@/views/ProfileView.vue'
import MyDuesView from '@/views/MyDuesView.vue'
import LoginView from '@/views/LoginView.vue'

// Every route's view is mounted once with the API stubbed, so a view that throws in setup() or in its
// first render fails here instead of shipping. This is the gap the two Messages bugs fell through: no
// test mounted a view, and a bare identifier or an i18n message vue-i18n cannot compile only fails at
// runtime -- the build and the rest of the suite both pass straight over it.
vi.mock('@/services/api', () => {
  const MEMBER = {
    id: 1, name: 'Test Member', email: 'test@example.com', phone: '',
    joinDate: '2026-01-01', status: 'MEMBER', consecutiveMonthsMissed: 0
  }
  // Shapes the page- and stats-shaped endpoints are read as; anything else resolves to an empty list.
  const SHAPES = {
    getDashboardStats: () => ({ totalMembers: 0, activeMembers: 0, overdueMembers: 0, monthlyRevenue: 0 }),
    getPaymentsPage: () => ({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 }),
    getCommunicationDeliveries: () => ({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 }),
    getPaidMonths: () => ({}),
    getPaymentSummary: () => ({}),
    getCurrentUser: () => ({}),
    getMember: () => ({ ...MEMBER, householdId: null }),
    getHousehold: () => ({ id: 1, name: 'Test household', city: '', members: [] }),
    getMyDues: () => ({ ...MEMBER, householdName: null, monthsBehind: 0, payments: [] })
  }
  const built = new Map()
  return {
    default: new Proxy({}, {
      get(_target, prop) {
        if (typeof prop !== 'string') return undefined
        if (!built.has(prop)) {
          const shape = SHAPES[prop]
          built.set(prop, vi.fn(() => Promise.resolve(shape ? shape() : [])))
        }
        return built.get(prop)
      }
    })
  }
})

// The app's own router carries auth guards, which would bounce every mount to /login; this one keeps
// only the paths, so RouterLink resolves and useRoute() answers while the guards stay out of the way.
const ROUTER_PATHS = ['/dashboard', '/members', '/members/:id', '/households', '/payments',
  '/communications', '/activity', '/more', '/profile', '/my-dues', '/login']

const VIEWS = [
  ['Dashboard', Dashboard, '/dashboard'],
  ['MembersView', MembersView, '/members'],
  ['MemberDetailView', MemberDetailView, '/members/1'],
  ['HouseholdsView', HouseholdsView, '/households'],
  ['PaymentsView', PaymentsView, '/payments'],
  ['CommunicationsView', CommunicationsView, '/communications'],
  ['ActivityView', ActivityView, '/activity'],
  ['MoreView', MoreView, '/more'],
  ['ProfileView', ProfileView, '/profile'],
  ['MyDuesView', MyDuesView, '/my-dues'],
  ['LoginView', LoginView, '/login']
]

// What must never reach the console: a throw Vue swallowed, a message vue-i18n refused to compile,
// a missing identifier or a call to something that is not a function.
const FATAL = /Unhandled error|Message compilation error|is not defined|is not a function|Failed to resolve component/

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: ROUTER_PATHS.map(path => ({ path, component: { template: '<div />' } }))
  })
}

describe('every route mounts and paints', () => {
  let spies
  let messages

  beforeEach(() => {
    setActivePinia(createPinia())
    messages = []
    spies = ['error', 'warn'].map(level =>
      vi.spyOn(console, level).mockImplementation((...args) => messages.push(String(args[0])))
    )
  })

  afterEach(() => spies.forEach(spy => spy.mockRestore()))

  it.each(VIEWS)('%s', async (_name, View, path) => {
    const router = makeRouter()
    await router.push(path)
    await router.isReady()

    const wrapper = mount(View, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text().trim().length).toBeGreaterThan(0)
    expect(messages.filter(m => FATAL.test(m))).toEqual([])
  })
})
