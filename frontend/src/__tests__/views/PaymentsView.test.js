import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PaymentsView from '@/views/PaymentsView.vue'
import { useAuthStore } from '@/stores/authStore'
import api from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    getPaymentsPage: vi.fn(),
    getPaymentSummary: vi.fn(),
    getPaidMonths: vi.fn(),
    getPaymentsByMember: vi.fn(),
    getMembers: vi.fn(),
    getCollectedByMonth: vi.fn()
  }
}))

const payment = (id, over = {}) => ({ id, amount: 10, period: '2026-10', paymentDate: '2026-10-02', paymentMethod: 'CASH', member: { id, name: `Member ${id}` }, ...over })
const page = (content, totalElements = content.length) => ({ content, page: 0, size: 25, totalElements, totalPages: 1 })
const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

describe('PaymentsView server paging', () => {
  let router
  let wrapper

  const mountAt = async (url, role = 'VOLUNTEER') => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    auth.user = { role, email: 'v@example.com' }
    auth.isAuthenticated = true
    router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/payments', component: PaymentsView }] })
    router.push(url)
    await router.isReady()
    wrapper = mount(PaymentsView, { global: { plugins: [pinia, router], stubs: { CollectedChart: true } } })
    await flushPromises()
  }
  const lastParams = () => api.getPaymentsPage.mock.calls.at(-1)[0]

  beforeEach(() => {
    vi.clearAllMocks()
    api.getPaymentSummary.mockResolvedValue({ thisMonth: 10, allTime: 100, average: 10, count: 40 })
    api.getPaymentsPage.mockResolvedValue(page([payment(1)], 40))
  })
  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  it('asks the server for the page, size, search, method and sort in the URL, and shows the summary figures', async () => {
    await mountAt('/payments?page=3&size=10&search=%20keb%20&method=check&sort=amount,asc')
    expect(api.getPaymentsPage).toHaveBeenCalledTimes(1)
    expect(lastParams()).toEqual({ page: 2, size: 10, search: 'keb', method: 'CHECK', sort: 'amount,asc' })
    expect(wrapper.text()).toContain('$100.00')
    expect(wrapper.find('th[aria-sort="ascending"]').text()).toContain('Amount')
  })

  it('waits 300 ms after the last key, asks once, goes back to page 1 and writes the search to the URL', async () => {
    await mountAt('/payments?page=2&size=10')
    vi.useFakeTimers()
    const input = wrapper.find('#filter-search')
    await input.setValue('a')
    await input.setValue('ab ')
    vi.advanceTimersByTime(299)
    expect(api.getPaymentsPage).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(api.getPaymentsPage).toHaveBeenCalledTimes(2)
    expect(lastParams()).toEqual({ page: 0, size: 10, search: 'ab', sort: 'paymentDate,desc' })
    expect(router.currentRoute.value.query).toEqual({ size: '10', search: 'ab' })
  })

  it('clicking a column header sorts on the server from page 1 and flips on the second click', async () => {
    await mountAt('/payments?page=2')
    const memberHeader = () => wrapper.findAll('th').find(th => th.text() === 'Member' && th.attributes('aria-sort'))
    await memberHeader().find('button').trigger('click')
    await flushPromises()
    expect(lastParams()).toMatchObject({ page: 0, sort: 'member,asc' })
    expect(router.currentRoute.value.query).toEqual({ sort: 'member,asc' })
    await memberHeader().find('button').trigger('click')
    await flushPromises()
    expect(lastParams().sort).toBe('member,desc')
  })

  it('ignores an older answer that arrives after a newer one', async () => {
    await mountAt('/payments')
    const slow = deferred()
    api.getPaymentsPage.mockReturnValueOnce(slow.promise).mockResolvedValueOnce(page([payment(2, { member: { id: 2, name: 'Newer answer' } })]))
    await wrapper.find('#filter-method').setValue('CASH')
    await wrapper.find('#filter-method').setValue('CHECK')
    await flushPromises()
    expect(wrapper.text()).toContain('Newer answer')
    slow.resolve(page([payment(3, { member: { id: 3, name: 'Older answer' } })]))
    await flushPromises()
    expect(wrapper.text()).toContain('Newer answer')
    expect(wrapper.text()).not.toContain('Older answer')
  })

  it('shows the error with Try again, and the rows come back on retry', async () => {
    api.getPaymentsPage.mockRejectedValueOnce(new Error('down'))
    await mountAt('/payments')
    expect(wrapper.text()).toContain('The payments did not load')
    await wrapper.findAll('button').find(b => b.text() === 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('The payments did not load')
    expect(wrapper.text()).toContain('Member 1')
  })

  it('keeps the old rows (dimmed) while the next page loads', async () => {
    await mountAt('/payments')
    const next = deferred()
    api.getPaymentsPage.mockReturnValueOnce(next.promise)
    await wrapper.find('#filter-method').setValue('CASH')
    expect(wrapper.text()).toContain('Member 1')
    expect(wrapper.find('[aria-busy="true"]').classes()).toContain('opacity-60')
    next.resolve(page([payment(5)]))
    await flushPromises()
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
  })

  it('the Record payment dialog reads the paid months, and the amount comes from the chosen member\'s own newest payment', async () => {
    api.getMembers.mockResolvedValue([{ id: 7, name: 'Abebe', status: 'MEMBER', joinDate: '2026-01-05', consecutiveMonthsMissed: 0 }])
    api.getPaidMonths.mockResolvedValue({ 7: ['2026-09'] })
    api.getPaymentsByMember.mockResolvedValue([payment(1, { amount: 20, paymentDate: '2026-09-01' }), payment(2, { amount: 35, paymentDate: '2026-10-01' })])
    await mountAt('/payments', 'STAFF')
    wrapper.vm.openRecord()
    wrapper.vm.form.memberId = '7'
    await flushPromises()
    expect(api.getPaidMonths).toHaveBeenCalledWith(12)
    expect(api.getPaymentsByMember).toHaveBeenCalledWith('7')
    expect(wrapper.vm.form.amount).toBe('35')
    // a typed amount is never replaced
    wrapper.vm.form.memberId = ''
    await flushPromises()
    expect(wrapper.vm.form.amount).toBe('')
    wrapper.vm.form.amount = '12'
    wrapper.vm.form.memberId = '7'
    await flushPromises()
    expect(wrapper.vm.form.amount).toBe('12')
  })
})
