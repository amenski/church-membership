import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { AxiosError } from 'axios'
import { axiosInstance } from '@/services/api'
import { useAuthStore, useAppStore } from '@/stores/index.js'

// Adapter that never touches the network: answers each URL from `routes`
function respondWith(routes) {
  axiosInstance.defaults.adapter = async (config) => {
    const route = routes[config.url]
    const status = typeof route === 'number' ? route : route.status
    const data = typeof route === 'number' ? undefined : route.data
    if (status < 400) {
      return { status, data, headers: {}, config, statusText: 'OK' }
    }
    const response = { status, data, headers: {}, config }
    throw new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response)
  }
}

describe('api response interceptor', () => {
  let originalAdapter
  let addNotification

  beforeEach(() => {
    setActivePinia(createPinia())
    originalAdapter = axiosInstance.defaults.adapter
    addNotification = vi.spyOn(useAppStore(), 'addNotification')
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    // Already on /login, so a failed refresh does not try to navigate
    window.history.pushState({}, '', '/login')
  })

  afterEach(() => {
    axiosInstance.defaults.adapter = originalAdapter
    vi.restoreAllMocks()
  })

  function signIn() {
    const authStore = useAuthStore()
    authStore.user = { role: 'MEMBER' }
    authStore.isAuthenticated = true
  }

  it('403 shows one Access Denied notification with the server detail', async () => {
    respondWith({ '/members': { status: 403, data: { detail: 'Role STAFF required' } } })

    await expect(axiosInstance.get('/members')).rejects.toBeDefined()

    expect(addNotification).toHaveBeenCalledTimes(1)
    expect(addNotification).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Access Denied', message: 'Role STAFF required' })
    )
  })

  it('401 on the login URL neither refreshes nor notifies', async () => {
    const calls = []
    respondWith({ '/auth/login': 401, '/auth/refresh': 200 })
    const adapter = axiosInstance.defaults.adapter
    axiosInstance.defaults.adapter = (config) => {
      calls.push(config.url)
      return adapter(config)
    }

    await expect(axiosInstance.post('/auth/login', {})).rejects.toBeDefined()

    expect(calls).toEqual(['/auth/login'])
    expect(addNotification).not.toHaveBeenCalled()
  })

  it('401 with failed refresh while signed in shows one Session Expired', async () => {
    signIn()
    respondWith({ '/members': 401, '/auth/refresh': 401 })

    await expect(axiosInstance.get('/members')).rejects.toBeDefined()

    expect(addNotification).toHaveBeenCalledTimes(1)
    expect(addNotification).toHaveBeenCalledWith(expect.objectContaining({ title: 'Session Expired' }))
    expect(useAuthStore().isAuthenticated).toBe(false)
  })

  // jsdom's window.location cannot be redefined, so swap it for a recording stub
  function stubLocation(pathname) {
    const original = window.location
    // This test environment has no usable Web Storage, which the refresh-failure path clears
    const storage = { removeItem: () => {} }
    vi.stubGlobal('localStorage', storage)
    vi.stubGlobal('sessionStorage', storage)
    const stub = { pathname, href: pathname }
    Object.defineProperty(window, 'location', { configurable: true, value: stub })
    return {
      stub,
      restore: () => {
        Object.defineProperty(window, 'location', { configurable: true, value: original })
        vi.unstubAllGlobals()
      }
    }
  }

  it('401 with failed refresh while not signed in shows no notification and does not redirect', async () => {
    const { stub, restore } = stubLocation('/')
    try {
      respondWith({ '/users/me': 401, '/auth/refresh': 401 })

      await expect(axiosInstance.get('/users/me')).rejects.toBeDefined()

      expect(addNotification).not.toHaveBeenCalled()
      expect(stub.href).toBe('/')
    } finally {
      restore()
    }
  })

  it('401 with failed refresh while signed in redirects to the expired-session page', async () => {
    const { stub, restore } = stubLocation('/dashboard')
    try {
      signIn()
      respondWith({ '/members': 401, '/auth/refresh': 401 })

      await expect(axiosInstance.get('/members')).rejects.toBeDefined()

      expect(addNotification).toHaveBeenCalledTimes(1)
      expect(addNotification).toHaveBeenCalledWith(expect.objectContaining({ title: 'Session Expired' }))
      expect(stub.href).toBe('/login?session=expired')
    } finally {
      restore()
    }
  })

  it('401 on a signed-in request refreshes once, retries the request and resolves', async () => {
    signIn()
    const calls = []
    let membersCalls = 0
    axiosInstance.defaults.adapter = async (config) => {
      calls.push(config.url)
      if (config.url === '/members') {
        membersCalls++
        if (membersCalls === 1) {
          const response = { status: 401, data: undefined, headers: {}, config }
          throw new AxiosError('Request failed with status code 401', 'ERR_BAD_REQUEST', config, null, response)
        }
      }
      return { status: 200, data: [], headers: {}, config, statusText: 'OK' }
    }

    const response = await axiosInstance.get('/members')

    expect(response.status).toBe(200)
    expect(calls).toEqual(['/members', '/auth/refresh', '/members'])
    expect(addNotification).not.toHaveBeenCalled()
  })

  it('401 that persists after a successful refresh shows one Unauthorized', async () => {
    signIn()
    respondWith({
      '/members': { status: 401, data: { detail: 'Full authentication is required' } },
      '/auth/refresh': 200
    })

    await expect(axiosInstance.get('/members')).rejects.toBeDefined()

    expect(addNotification).toHaveBeenCalledTimes(1)
    expect(addNotification).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Unauthorized', message: 'Full authentication is required' })
    )
  })

  it('400 ProblemDetail puts its detail in error.message and exposes fieldErrors', async () => {
    const errors = [{ field: 'email', message: 'must be a valid email' }]
    respondWith({ '/members': { status: 400, data: { status: 400, detail: 'One or more fields are invalid', errors } } })

    const error = await axiosInstance.post('/members', {}).catch((e) => e)

    expect(error.message).toBe('One or more fields are invalid')
    expect(error.fieldErrors).toEqual(errors)
  })

  it('400 without errors gives an empty fieldErrors list', async () => {
    respondWith({ '/members': { status: 400, data: { detail: 'Business rule broken', code: 'X' } } })

    const error = await axiosInstance.post('/members', {}).catch((e) => e)

    expect(error.message).toBe('Business rule broken')
    expect(error.fieldErrors).toEqual([])
  })

  it('a network error keeps its own message', async () => {
    axiosInstance.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config)
    }

    const error = await axiosInstance.get('/members').catch((e) => e)

    expect(error.message).toBe('Network Error')
    expect(error.fieldErrors).toBeUndefined()
  }, 15000)
})

describe('api CSRF header', () => {
  let originalAdapter
  let sent

  beforeEach(() => {
    setActivePinia(createPinia())
    originalAdapter = axiosInstance.defaults.adapter
    sent = null
    axiosInstance.defaults.adapter = async (config) => {
      sent = config
      return { status: 200, data: {}, headers: {}, config, statusText: 'OK' }
    }
    document.cookie = 'XSRF-TOKEN=token-from-cookie; path=/'
  })

  afterEach(() => {
    axiosInstance.defaults.adapter = originalAdapter
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.restoreAllMocks()
  })

  it.each(['post', 'put', 'patch', 'delete'])('a %s request carries X-XSRF-TOKEN read from the cookie', async (method) => {
    await axiosInstance[method]('/members/1')

    expect(sent.headers['X-XSRF-TOKEN']).toBe('token-from-cookie')
  })

  it('a GET request does not carry X-XSRF-TOKEN', async () => {
    await axiosInstance.get('/members')

    expect(sent.headers['X-XSRF-TOKEN']).toBeUndefined()
  })
})
