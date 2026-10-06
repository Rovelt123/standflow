import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { authenticate, clearAuth, getAuthToken, getCurrentUser } from '../src/pages/public/authApi.js'
import { submitApplication } from '../src/pages/ansoegning/applicationApi.js'

const tokenKey = 'standflow.auth.token'
const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 86400 })).toString('base64url')}.signature`

//--------------------------------------------------------------

function storage() {
  const entries = new Map()
  return {
    getItem: key => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: key => entries.delete(key),
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage() })
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: storage() })
})

//--------------------------------------------------------------

test('registration sends separate boolean consents and saves a session token', async () => {
  const fields = { email: 'test@example.dk', password: 'Password!', acceptTerms: true, acceptPrivacy: true, acceptMarketing: false }
  await authenticate(fields, true, async (url, options) => {
    assert.equal(url, '/api/users/auth/register')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), fields)
    return { status: 201, json: async () => ({ data: { token } }) }
  })
  assert.equal(globalThis.sessionStorage.getItem(tokenKey), token)
  assert.equal(globalThis.localStorage.getItem(tokenKey), null)
  assert.equal(getAuthToken(), token)
})

//--------------------------------------------------------------

test('remember me persists across sessions; ordinary login removes the remembered token', async () => {
  const response = async () => ({ status: 200, json: async () => ({ data: { token } }) })
  await authenticate({ rememberMe: true }, false, response)
  assert.equal(globalThis.localStorage.getItem(tokenKey), token)
  assert.equal(globalThis.sessionStorage.getItem(tokenKey), null)
  assert.equal(getAuthToken(), token)
  await authenticate({ rememberMe: false }, false, response)
  assert.equal(globalThis.localStorage.getItem(tokenKey), null)
  assert.equal(globalThis.sessionStorage.getItem(tokenKey), token)
  clearAuth()
  assert.equal(getAuthToken(), null)
})

//--------------------------------------------------------------

test('expired and malformed tokens are removed', () => {
  for (const invalid of ['broken', `header.${Buffer.from('{"exp":1}').toString('base64url')}.signature`]) {
    globalThis.localStorage.setItem(tokenKey, invalid)
    assert.equal(getAuthToken(), null)
    assert.equal(globalThis.localStorage.getItem(tokenKey), null)
  }
})

//--------------------------------------------------------------

test('rejected login, network failures and malformed responses never establish a session', async () => {
  for (const status of [400, 401, 409, 500]) {
    await assert.rejects(authenticate({}, false, async () => ({ status, text: async () => 'Afvist' })))
    assert.equal(getAuthToken(), null)
  }
  await assert.rejects(authenticate({}, false, async () => { throw new Error('offline') }), /kontakte serveren/)
  await assert.rejects(authenticate({}, false, async () => ({ status: 200, json: async () => ({}) })), /svar kunne ikke læses/)
  assert.equal(getAuthToken(), null)
})

//--------------------------------------------------------------

test('application sends the bearer token and clears rejected sessions', async () => {
  globalThis.sessionStorage.setItem(tokenKey, token)
  await assert.rejects(submitApplication({}, async (url, options) => {
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    return { status: 401 }
  }), /Log ind igen/)
  assert.equal(getAuthToken(), null)
})

//--------------------------------------------------------------

test('loads the current profile using the session token and rejects expired sessions', async () => {
  globalThis.sessionStorage.setItem(tokenKey, token)
  const profile = { email: 'test@example.dk', company: 'Test' }
  assert.deepEqual(await getCurrentUser(async (url, options) => {
    assert.equal(url, '/api/users/me')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    return { status: 200, json: async () => profile }
  }), profile)
  await assert.rejects(getCurrentUser(async () => ({ status: 401 })), /Log ind igen/)
  assert.equal(getAuthToken(), null)
})
