import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { unsubscribeFromNewsletter } from '../src/pages/public/newsletterApi.js'
import { audiences, deleteTemplate, getNewsletterRecipients, getTemplate, getTemplates, newsletterPayload, renderTemplate, saveTemplate, sendNewsletter, standTypes, validateTemplate } from '../src/pages/kontrolpanel/newsletterAdminApi.js'

const id = '12345678-1234-1234-1234-123456789abc'
const template = { id, name: 'Nyheder', subject: 'Hej <Firstname>', body: '<Company>\n<Email>' }
const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 86400 })).toString('base64url')}.signature`

//--------------------------------------------------------------

beforeEach(() => {
  for (const name of ['localStorage', 'sessionStorage']) {
    const entries = new Map()
    Object.defineProperty(globalThis, name, { configurable: true, value: {
      getItem: key => entries.get(key) ?? null,
      setItem: (key, value) => entries.set(key, value),
      removeItem: key => entries.delete(key),
    } })
  }
  globalThis.sessionStorage.setItem('standflow.auth.token', token)
})

//--------------------------------------------------------------

function reply(status, result) {
  return { status, json: async () => result, text: async () => 'Backend validation error' }
}

//--------------------------------------------------------------

test('public unsubscribe sends only the URL token without authentication', async () => {
  const result = await unsubscribeFromNewsletter('example-token', async (url, options) => {
    assert.equal(url, '/api/newsletter/unsubscribe')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Authorization, undefined)
    assert.deepEqual(JSON.parse(options.body), { token: 'example-token' })
    return reply(200, { marketingConsent: false })
  })
  assert.deepEqual(result, { marketingConsent: false })
})

//--------------------------------------------------------------

test('missing tokens never make a request; invalid, expired and consumed tokens have safe feedback', async () => {
  for (const missing of [null, undefined, '', '   ']) {
    await assert.rejects(unsubscribeFromNewsletter(missing, () => assert.fail('Unexpected request')), /ufuldstændigt/)
  }
  for (const status of [400, 404]) {
    await assert.rejects(unsubscribeFromNewsletter('secret-token', async () => ({
      status, text: async () => 'secret-token',
    })), error => /allerede brugt/.test(error.message) && !error.message.includes('secret-token'))
  }
})

//--------------------------------------------------------------

test('unsubscribe never reports success for malformed or unconfirmed responses', async () => {
  for (const body of [{}, null, { marketingConsent: true }, { marketingConsent: 'false' }]) {
    await assert.rejects(unsubscribeFromNewsletter('token', async () => reply(200, body)), /kunne ikke bekræftes/)
  }
  await assert.rejects(unsubscribeFromNewsletter('token', async () => { throw new Error('offline') }), /Forbindelsen/)
  await assert.rejects(unsubscribeFromNewsletter('token', async () => reply(500)), /kunne ikke gennemføres/)
})

//--------------------------------------------------------------

test('template reads unwrap the actual BaseController envelopes and preserve an empty list', async () => {
  for (const list of [[], [template]]) {
    assert.deepEqual(await getTemplates(async (url, options) => {
      assert.equal(url, '/api/template')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      return reply(200, { data: { data: list } })
    }), list)
  }
  assert.deepEqual(await getTemplate(id, async (url) => {
    assert.equal(url, `/api/template/${id}`)
    return reply(200, { data: { data: template } })
  }), template)
  await assert.rejects(getTemplates(async () => reply(200, {})), /kunne ikke læses/)
})

//--------------------------------------------------------------

test('template create and update send only supported text fields and consume direct DTOs', async () => {
  for (const existingId of [undefined, id]) {
    assert.deepEqual(await saveTemplate(existingId, template, async (url, options) => {
      assert.equal(url, existingId ? `/api/template/${id}` : '/api/template')
      assert.equal(options.method, existingId ? 'PUT' : 'POST')
      assert.deepEqual(JSON.parse(options.body), { name: template.name, subject: template.subject, body: template.body })
      return reply(existingId ? 200 : 201, template)
    }), template)
  }
})

//--------------------------------------------------------------

test('delete accepts 204 without trying to read a JSON body', async () => {
  assert.equal(await deleteTemplate(id, async (url, options) => {
    assert.equal(url, `/api/template/${id}`)
    assert.equal(options.method, 'DELETE')
    return { status: 204, json: () => assert.fail('No response body') }
  }), null)
  await assert.rejects(deleteTemplate(id, async () => reply(404)), /Backend validation error/)
})

//--------------------------------------------------------------

test('validation sends subject and body only and preserves backend placeholder errors', async () => {
  const validation = { valid: false, unknownVariables: ['<Unknown>'] }
  assert.deepEqual(await validateTemplate(template, async (url, options) => {
    assert.equal(url, '/api/template/validate')
    assert.deepEqual(JSON.parse(options.body), { subject: template.subject, body: template.body })
    return reply(200, validation)
  }), validation)
})

//--------------------------------------------------------------

test('preview uses the saved template render endpoint and actual backend text', async () => {
  const recipient = { firstName: 'Ola', lastName: 'Nordmann', company: 'Nord', email: 'ola@example.com' }
  const preview = { subject: 'Backend subject', body: 'Backend\nbody', unknownVariables: [] }
  assert.deepEqual(await renderTemplate(id, recipient, async (url, options) => {
    assert.equal(url, `/api/template/${id}/render`)
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), recipient)
    return reply(200, preview)
  }), preview)
})

//--------------------------------------------------------------

test('individual recipients use customer IDs from existing threads, not application IDs', async () => {
  const thread = { customerId: id, customerName: 'Ola', company: 'Nord' }
  assert.deepEqual(await getNewsletterRecipients(async url => {
    assert.equal(url, '/api/messages/threads?sort=name')
    return reply(200, [thread, thread])
  }), [{ id, name: 'Ola', company: 'Nord' }])
})

//--------------------------------------------------------------

test('all six audiences send exactly their supported selectors, with deduplicated UUIDs', async () => {
  assert.equal(audiences.length, 6)
  for (const { value: audience } of audiences) {
    const expected = { audience, templateId: id }
    if (audience === 'INDIVIDUAL') expected.recipientIds = [id]
    if (audience === 'CATEGORY') expected.category = 'A'
    const selection = { audience, templateId: id, recipientIds: [id, id.toUpperCase(), ' '], category: 'A' }
    assert.deepEqual(newsletterPayload(selection), expected)
    const totals = { sentTo: 12, skippedNoConsent: 3, failedToSend: 2 }
    assert.deepEqual(await sendNewsletter(selection, async (url, options) => {
      assert.equal(url, '/api/newsletter')
      assert.equal(options.method, 'POST')
      assert.deepEqual(JSON.parse(options.body), expected)
      return reply(202, totals)
    }), totals)
  }
  for (const category of standTypes) {
    assert.equal(newsletterPayload({ audience: 'CATEGORY', templateId: id, category }).category, category)
  }
})

//--------------------------------------------------------------

test('invalid selections cannot send newsletters', async () => {
  for (const selection of [
    { audience: 'ALL_USERS', templateId: id }, { audience: 'ALL_APPLICANTS' },
    { audience: 'INDIVIDUAL', templateId: id, recipientIds: [] },
    { audience: 'INDIVIDUAL', templateId: id, recipientIds: ['not-a-user-id'] },
    { audience: 'CATEGORY', templateId: id, category: 'OTHER' },
  ]) {
    await assert.rejects(sendNewsletter(selection, () => assert.fail('Invalid selection sent')))
  }
})

//--------------------------------------------------------------

test('delivery is never retried automatically, including malformed delivery totals', async () => {
  for (const response of [reply(500), reply(202, {}), reply(202, { sentTo: -1, skippedNoConsent: 0, failedToSend: 0 })]) {
    let calls = 0
    await assert.rejects(sendNewsletter({ audience: 'ALL_APPLICANTS', templateId: id }, async () => { calls++; return response }))
    assert.equal(calls, 1)
  }
})

//--------------------------------------------------------------

test('admin operations surface forbidden responses and clear expired sessions', async () => {
  await assert.rejects(getTemplates(async () => reply(403)), /ikke adgang/)
  await assert.rejects(saveTemplate(undefined, template, async () => reply(400)), /Backend validation error/)
  await assert.rejects(getTemplates(async () => reply(401)), /session er udløbet/)
  await assert.rejects(getTemplates(() => assert.fail('Unauthenticated request')), /Log ind igen/)
})
