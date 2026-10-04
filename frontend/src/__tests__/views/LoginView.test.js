import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from '@/views/LoginView.vue'

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
