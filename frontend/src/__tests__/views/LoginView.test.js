import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/authStore'

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/login', component: LoginView }, { path: '/', component: { template: '<div />' } }]
  })
  router.push(path)
  await router.isReady()
  return mount(LoginView, { global: { plugins: [createPinia(), router] } })
}

describe('LoginView session notice', () => {
  it('tells the user their session expired when sent here with ?session=expired', async () => {
    const wrapper = await mountAt('/login?session=expired')
    expect(wrapper.text()).toContain('Your session expired. Sign in again.')
  })

  it('shows no notice on a normal visit', async () => {
    const wrapper = await mountAt('/login')
    expect(wrapper.text()).not.toContain('session expired')
  })
})

describe('LoginView after signing in', () => {
  async function signInAt(path) {
    // The real router sends '/' to '/login', and a guard sends a signed-in user from there to home
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', redirect: '/login' },
        { path: '/login', component: LoginView },
        { path: '/dashboard', component: { template: '<div />' } },
        { path: '/members', component: { template: '<div />' } }
      ]
    })
    router.push(path)
    await router.isReady()
    const pinia = createPinia()
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })
    const auth = useAuthStore(pinia)
    auth.login = vi.fn(async () => {
      auth.$patch({ isAuthenticated: true, user: { role: 'ADMIN', email: 'a@b.org' } })
    })
    await wrapper.find('input[type="email"]').setValue('a@b.org')
    await wrapper.find('input[type="password"]').setValue('Secret-123!')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(auth.login).toHaveBeenCalled())
    await new Promise((resolve) => setTimeout(resolve, 20))
    return router.currentRoute.value.path
  }

  it('leaves the sign-in page for the home page', async () => {
    expect(await signInAt('/login')).toBe('/dashboard')
  })

  it('goes back to the page that asked for the sign-in', async () => {
    expect(await signInAt('/login?redirect=%2Fmembers')).toBe('/members')
  })
})
