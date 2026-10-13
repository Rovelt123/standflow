const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://standflow.api.roneu.dk:9595' : '/api')).replace(/\/+$/, '')
const tokenKey = 'standflow.auth.token'

//--------------------------------------------------------------

export function clearAuth() {
  globalThis.localStorage?.removeItem(tokenKey)
  globalThis.sessionStorage?.removeItem(tokenKey)
  globalThis.dispatchEvent?.(new Event('standflow-auth'))
}

//--------------------------------------------------------------

export function subscribeAuth(listener) {
  globalThis.addEventListener('standflow-auth', listener)
  globalThis.addEventListener('storage', listener)
  return () => {
    globalThis.removeEventListener('standflow-auth', listener)
    globalThis.removeEventListener('storage', listener)
  }
}

//--------------------------------------------------------------

export function replaceAuthToken(token) {
  const storage = globalThis.localStorage?.getItem(tokenKey) ? globalThis.localStorage : globalThis.sessionStorage
  storage.setItem(tokenKey, token)
  globalThis.dispatchEvent?.(new Event('standflow-auth'))
}

//--------------------------------------------------------------

export function getAuthToken() {
  const token = globalThis.sessionStorage?.getItem(tokenKey)
    || globalThis.localStorage?.getItem(tokenKey)
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (typeof payload.exp === 'number' && payload.exp * 1000 > Date.now()) return token
  } catch {
    // Malformed or expired sessions require a new login.
  }
  clearAuth()
  return null
}

//--------------------------------------------------------------

export async function authenticate(fields, register = false, fetchRequest = fetch) {
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}/users/auth/${register ? 'register' : 'login'}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(fields),
    })
  } catch {
    throw new Error('Kunne ikke kontakte serveren. Prøv igen.')
  }
  if (response.status !== (register ? 201 : 200)) {
    if ([400, 401, 409].includes(response.status)) {
      const message = await response.text().catch(() => '')
      throw new Error(message || 'Kontrollér dine oplysninger, og prøv igen.')
    }
    throw new Error('Kunne ikke logge ind eller oprette kontoen. Prøv igen senere.')
  }
  let token
  try {
    const result = await response.json()
    token = result?.data?.token
    if (typeof token !== 'string' || !token) throw new Error('Missing token')
  } catch {
    throw new Error('Serverens svar kunne ikke læses. Prøv at logge ind igen.')
  }
  clearAuth()
  const storage = !register && fields.rememberMe ? globalThis.localStorage : globalThis.sessionStorage
  storage.setItem(tokenKey, token)
  if (!getAuthToken()) throw new Error('Sessionen er ugyldig. Prøv at logge ind igen.')
  globalThis.dispatchEvent?.(new Event('standflow-auth'))
  return token
}

//--------------------------------------------------------------

export async function getCurrentUser(fetchRequest = fetch) {
  const token = getAuthToken()
  if (!token) throw new Error('Log ind igen for at hente dine brugeroplysninger.')
  const response = await fetchRequest(`${apiBaseUrl}/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (response.status === 401 || response.status === 403) {
    clearAuth()
    throw new Error('Log ind igen for at hente dine brugeroplysninger.')
  }
  if (response.status !== 200) throw new Error('Brugeroplysningerne kunne ikke hentes.')
  const user = await response.json()
  if (!user || typeof user.email !== 'string') throw new Error('Brugeroplysningerne kunne ikke læses.')
  return user
}
