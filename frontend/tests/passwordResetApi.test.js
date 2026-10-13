import test from 'node:test'
import assert from 'node:assert/strict'
import { requestPasswordReset, resetPassword } from '../src/pages/public/authApi.js'

test('requests a reset email without authentication', async () => {
  await requestPasswordReset('user@example.dk', async (url, options) => {
    assert.equal(url, '/api/users/auth/forgot-password')
    assert.equal(options.method, 'POST')
    assert.deepEqual(options.headers, { 'Content-Type': 'application/json' })
    assert.deepEqual(JSON.parse(options.body), { email: 'user@example.dk' })
    return { status: 200 }
  })
})

//--------------------------------------------------------------

test('submits the token and new password and clears the old session', async () => {
  const removed = []
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: { removeItem: key => removed.push(key) } })
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: { removeItem: key => removed.push(key) } })
  const fields = { token: 'reset-token', newPassword: 'NewPassword!', confirmPassword: 'NewPassword!' }
  await resetPassword(fields, async (url, options) => {
    assert.equal(url, '/api/users/auth/reset-password')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), fields)
    return { status: 204 }
  })
  assert.deepEqual(removed, ['standflow.auth.token', 'standflow.auth.token'])
})

//--------------------------------------------------------------

test('reports invalid links, server failures and network failures', async () => {
  await assert.rejects(resetPassword({}, async () => ({ status: 400, text: async () => 'Linket er udløbet' })), /udløbet/)
  await assert.rejects(requestPasswordReset('user@example.dk', async () => ({ status: 500 })), /Prøv igen senere/)
  await assert.rejects(requestPasswordReset('user@example.dk', async () => { throw new Error('offline') }), /kontakte serveren/)
})
