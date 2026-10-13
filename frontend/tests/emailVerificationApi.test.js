import test from 'node:test'
import assert from 'node:assert/strict'
import { authenticate, verifyEmail, resendVerification } from '../src/pages/public/authApi.js'

test('email verification submits the link token without authentication', async () => {
  await verifyEmail('verification-token', async (url, options) => {
    assert.equal(url, '/api/users/auth/verify-email')
    assert.equal(options.method, 'POST')
    assert.deepEqual(options.headers, { 'Content-Type': 'application/json' })
    assert.deepEqual(JSON.parse(options.body), { token: 'verification-token' })
    return { status: 204 }
  })
})

//--------------------------------------------------------------

test('resending only submits the email and accepts a generic success', async () => {
  await resendVerification('user@example.dk', async (url, options) => {
    assert.equal(url, '/api/users/auth/resend-verification')
    assert.deepEqual(JSON.parse(options.body), { email: 'user@example.dk' })
    return { status: 200 }
  })
})

//--------------------------------------------------------------

test('expired links, failed requests and unverified login display errors', async () => {
  await assert.rejects(verifyEmail('invalid', async () => ({ status: 400, text: async () => 'Linket er udløbet' })), /udløbet/)
  await assert.rejects(resendVerification('user@example.dk', async () => ({ status: 500 })), /Prøv igen senere/)
  await assert.rejects(verifyEmail('token', async () => { throw new Error('offline') }), /kontakte serveren/)
  await assert.rejects(authenticate({}, false, async () => ({ status: 403, text: async () => 'Bekræft din e-mailadresse' })), /Bekræft/)
  await assert.rejects(authenticate({}, true, async () => ({ status: 201, json: async () => ({ data: { token: 'unexpected' } }) })), /svar kunne ikke læses/)
})
