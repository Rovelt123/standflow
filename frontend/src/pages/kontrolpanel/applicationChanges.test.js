import test from 'node:test'
import assert from 'node:assert/strict'
import { saveApplicationChanges } from './applicationChanges.js'

const application = { id: 'test', standType: 'A', status: 'PENDING', comment: '', customerNote: '' }

test('one save persists comments and status before the stand type notification', async () => {
  const calls = []
  const updates = []
  const values = { comment: 'Intern', customerNote: 'Note', status: 'ACCEPTED', standType: 'B' }
  const result = await saveApplicationChanges(application, values, async (path, method, body) => {
    calls.push({ path, method, body })
    return path.endsWith('stand-type') ? { application: values, notificationStatus: 'SENT' } : body
  }, updated => updates.push(updated))
  assert.deepEqual(calls.map(call => call.path), ['/applications/test/comment', '/applications/test/status', '/applications/test/stand-type'])
  assert.equal(updates.length, 3)
  assert.equal(result, 'SENT')
})

//--------------------------------------------------------------

test('saving only a note never requests a stand type change', async () => {
  const calls = []
  await saveApplicationChanges(application, { ...application, customerNote: 'Ny note' }, async path => {
    calls.push(path)
    return application
  }, () => {})
  assert.deepEqual(calls, ['/applications/test/comment'])
  await saveApplicationChanges(application, application, () => assert.fail('Unchanged data must not be sent'), () => {})
})

//--------------------------------------------------------------

test('a save failure stops remaining requests and does not trigger mail', async () => {
  const calls = []
  await assert.rejects(saveApplicationChanges(application, { ...application, comment: 'Ny', standType: 'B' }, async path => {
    calls.push(path)
    throw new Error('Save failed')
  }, () => {}), /Save failed/)
  assert.deepEqual(calls, ['/applications/test/comment'])
})
