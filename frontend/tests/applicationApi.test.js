import test from 'node:test'
import assert from 'node:assert/strict'
import { submitApplication } from '../src/pages/ansoegning/applicationApi.js'

const receipt = { id: 'b4f0667a-e5ad-4e91-bbf5-542821389c49', status: 'PENDING', createdAt: '2026-10-05' }

test('posts JSON with the original field types and returns a confirmed receipt', async () => {
  const application = { cvr: '01234567', previousExhibitor: false, tables: 0, chairs: 4, website: null }
  const actual = await submitApplication(application, async (url, options) => {
    assert.equal(url, '/api/applications')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body), application)
    return { status: 201, json: async () => receipt }
  })
  assert.deepEqual(actual, receipt)
})

test('shows validation and closure messages including the next opening date', async () => {
  for (const status of [400, 409]) {
    const message = status === 400 ? 'CVR skal bestå af præcis 8 cifre.'
      : 'Ansøgninger er lukket. Der åbnes for ansøgninger igen 2026-11-01.'
    await assert.rejects(submitApplication({}, async () => ({ status, text: async () => message })),
      { message })
  }
})

test('does not show internal server errors or treat other success codes as a receipt', async () => {
  for (const status of [200, 204, 404, 500, 503]) {
    await assert.rejects(submitApplication({}, async () => ({ status, text: async () => 'SQL secrets' })),
      error => !error.message.includes('SQL secrets') && error.message.includes('oplysninger er bevaret'))
  }
})

test('handles network failure without silently retrying', async () => {
  let calls = 0
  await assert.rejects(submitApplication({}, async () => {
    calls += 1
    throw new Error('Network failure')
  }), /kan ikke bekræfte/)
  assert.equal(calls, 1)
})

test('does not confirm an unreadable or incomplete receipt', async () => {
  for (const body of [null, {}, { ...receipt, id: 'invalid' }, { ...receipt, status: 'APPROVED' }]) {
    await assert.rejects(submitApplication({}, async () => ({ status: 201, json: async () => body })),
      /før du sender igen/)
  }
  await assert.rejects(submitApplication({}, async () => ({ status: 201, json: async () => { throw new Error() } })),
    /før du sender igen/)
})
