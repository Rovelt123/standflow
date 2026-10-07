import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { getAuthToken } from '../src/pages/public/authApi.js'
import { deleteAccount, getMarketingConsent, unsubscribeMarketing, updateMarketingConsent } from '../src/pages/portal/portalApi.js'

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
  globalThis.sessionStorage.setItem(tokenKey, token)
})

//--------------------------------------------------------------

test('loads both consent values from the dedicated endpoint using the existing token', async () => {
  for (const marketingConsent of [true, false]) {
    assert.equal(await getMarketingConsent(async (url, options) => {
      assert.equal(url, '/api/users/me/consent')
      assert.equal(options.method, 'GET')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.body, undefined)
      return { status: 200, json: async () => ({ marketingConsent }) }
    }), marketingConsent)
  }
})

//--------------------------------------------------------------

test('patches only marketingConsent and returns the stored response without changing authentication', async () => {
  for (const marketingConsent of [true, false]) {
    const actual = await updateMarketingConsent(marketingConsent, async (url, options) => {
      assert.equal(url, '/api/users/me/consent')
      assert.equal(options.method, 'PATCH')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.headers['Content-Type'], 'application/json')
      assert.deepEqual(JSON.parse(options.body), { marketingConsent })
      return { status: 200, json: async () => ({ marketingConsent: !marketingConsent }) }
    })
    assert.equal(actual, !marketingConsent)
    assert.equal(getAuthToken(), token)
  }
})

//--------------------------------------------------------------

test('unsubscribe posts no body and leaves the session active on repeated requests', async () => {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    assert.equal(await unsubscribeMarketing(async (url, options) => {
      assert.equal(url, '/api/users/me/unsubscribe')
      assert.equal(options.method, 'POST')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.body, undefined)
      return { status: 200, json: async () => ({ marketingConsent: false }) }
    }), false)
    assert.equal(getAuthToken(), token)
  }
})

//--------------------------------------------------------------

test('deletion rejects missing or blank passwords and non-explicit confirmation before sending', async () => {
  for (const [password, confirmation] of [[undefined, true], ['', true], ['  ', true], [123, true], ['password', false], ['password', 'true']]) {
    await assert.rejects(deleteAccount(password, confirmation, async () => assert.fail('Must not send')), /bekræft permanent sletning/)
    assert.equal(getAuthToken(), token)
  }
})

//--------------------------------------------------------------

test('deletion sends exact JSON without a user ID, skips 204 JSON and clears both token stores with an auth event', async () => {
  globalThis.localStorage.setItem(tokenKey, token)
  const originalDispatch = globalThis.dispatchEvent
  const events = []
  globalThis.dispatchEvent = event => { events.push(event.type) }
  try {
    await deleteAccount(' password with spaces ', true, async (url, options) => {
      assert.equal(url, '/api/users/me')
      assert.equal(options.method, 'DELETE')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.headers['Content-Type'], 'application/json')
      assert.deepEqual(JSON.parse(options.body), { currentPassword: ' password with spaces ', confirmDelete: true })
      assert.equal(getAuthToken(), token)
      return { status: 204, json: () => assert.fail('204 must not be parsed') }
    })
    assert.equal(globalThis.localStorage.getItem(tokenKey), null)
    assert.equal(globalThis.sessionStorage.getItem(tokenKey), null)
    assert.deepEqual(events, ['standflow-auth'])
  } finally {
    if (originalDispatch) globalThis.dispatchEvent = originalDispatch
    else delete globalThis.dispatchEvent
  }
})

//--------------------------------------------------------------

test('failed operations show safe errors and preserve authentication', async () => {
  for (const operation of [getMarketingConsent, fetchRequest => updateMarketingConsent(true, fetchRequest), unsubscribeMarketing, fetchRequest => deleteAccount('wrong', true, fetchRequest)]) {
    for (const status of [400, 404, 500]) {
      await assert.rejects(operation(async () => ({ status, text: async () => 'Internal stack trace' })),
        error => !error.message.includes('Internal stack trace'))
      assert.equal(getAuthToken(), token)
    }
    await assert.rejects(operation(async () => { throw new Error('Network failure') }), /Forbindelsen blev afbrudt/)
    assert.equal(getAuthToken(), token)
  }
})

//--------------------------------------------------------------

test('all GDPR operations reuse global 401 cleanup and reject missing authentication', async () => {
  for (const operation of [getMarketingConsent, fetchRequest => updateMarketingConsent(false, fetchRequest), unsubscribeMarketing, fetchRequest => deleteAccount('password', true, fetchRequest)]) {
    globalThis.sessionStorage.setItem(tokenKey, token)
    await assert.rejects(operation(async () => ({ status: 401 })), /session er udløbet/)
    assert.equal(getAuthToken(), null)
    await assert.rejects(operation(async () => assert.fail('Unauthenticated request')), /Log ind igen/)
  }
})

//--------------------------------------------------------------

test('unreadable consent never becomes a false success and deletion requires exactly 204', async () => {
  for (const result of [null, {}, { marketingConsent: 'false' }]) {
    await assert.rejects(getMarketingConsent(async () => ({ status: 200, json: async () => result })), /Samtykket kunne ikke læses/)
  }
  await assert.rejects(updateMarketingConsent(true, async () => ({ status: 200, json: async () => { throw new Error('Invalid JSON') } })), /Serverens svar kunne ikke læses/)
  await assert.rejects(deleteAccount('password', true, async () => ({ status: 200, json: () => assert.fail('Unexpected status') })), /Handlingen kunne ikke gennemføres/)
  assert.equal(getAuthToken(), token)
})
