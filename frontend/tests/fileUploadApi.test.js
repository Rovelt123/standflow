/* global File, FormData, sessionStorage */
import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { portalRequest, uploadFiles, validateSelectedFiles } from '../src/pages/portal/portalApi.js'

const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 86400 })).toString('base64url')}.signature`

//--------------------------------------------------------------

beforeEach(() => {
  const storage = () => {
    const data = new Map()
    return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
  }
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: storage() })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage() })
  sessionStorage.setItem('standflow.auth.token', token)
})

//--------------------------------------------------------------

test('all three files use one multipart request with browser-generated content type and JWT', async () => {
  const files = ['a.pdf', 'b.png', 'c.jpeg'].map(name => new File(['content'], name))
  let requests = 0
  const uploaded = await uploadFiles(files, async (url, options) => {
    requests++
    assert.equal(url, '/api/files')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.headers['Content-Type'], undefined)
    assert.ok(options.body instanceof FormData)
    assert.deepEqual(options.body.getAll('files').map(file => file.name), files.map(file => file.name))
    return { status: 201, json: async () => ({ files: files.map((file, index) => ({ id: `id-${index}`, originalName: file.name })) }) }
  })
  assert.equal(requests, 1)
  assert.equal(uploaded.length, 3)
  await portalRequest('/messages', 'POST', { subject: 'Subject', body: 'Body', attachmentIds: uploaded.map(file => file.id) }, async (url, options) => {
    assert.equal(url, '/api/messages')
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body).attachmentIds, ['id-0', 'id-1', 'id-2'])
    return { status: 201, json: async () => ({ id: 'message' }) }
  })
})

//--------------------------------------------------------------

test('selection validates format, empty files, exact size, oversize, and maximum count', () => {
  validateSelectedFiles([new File([new Uint8Array(1048576)], 'exact.PDF')])
  assert.throws(() => validateSelectedFiles([]), /1 og 3/)
  assert.throws(() => validateSelectedFiles(Array.from({ length: 4 }, () => new File(['x'], 'a.pdf'))), /1 og 3/)
  assert.throws(() => validateSelectedFiles([new File([], 'empty.png')]), /Tomme/)
  assert.throws(() => validateSelectedFiles([new File(['x'], 'wrong.txt')]), /PDF/)
  assert.throws(() => validateSelectedFiles([new File([new Uint8Array(1048577)], 'large.pdf')]), /1 MiB/)
})

//--------------------------------------------------------------

test('invalid selections make no request and upload errors remain readable', async () => {
  await assert.rejects(uploadFiles([new File(['x'], 'a.exe')], async () => assert.fail('Unexpected request')), /PDF/)
  for (const status of [400, 403, 413, 415]) {
    await assert.rejects(uploadFiles([new File(['x'], 'a.pdf')], async () => ({ status, text: async () => 'Readable validation error' })), /Readable validation error/)
  }
  await assert.rejects(uploadFiles([new File(['x'], 'a.pdf')], async () => ({ status: 500, text: async () => 'Internal storage path' })), error => !error.message.includes('Internal storage'))
  await assert.rejects(uploadFiles([new File(['x'], 'a.pdf')], async () => ({ status: 401 })), /session er udløbet/)
})

//--------------------------------------------------------------

test('missing or incomplete upload metadata never indicates success', async () => {
  const files = [new File(['x'], 'a.pdf'), new File(['x'], 'b.pdf')]
  for (const result of [null, {}, { files: [] }, { files: [{ id: 'same' }, { id: 'same' }] }, { files: [{}, {}] }]) {
    await assert.rejects(uploadFiles(files, async () => ({ status: 201, json: async () => result })), /ikke sendt/)
  }
})

//--------------------------------------------------------------

test('existing text-only JSON requests remain unchanged', async () => {
  await portalRequest('/messages', 'POST', { subject: 'Subject', body: 'Body' }, async (url, options) => {
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body), { subject: 'Subject', body: 'Body' })
    return { status: 201, json: async () => ({ id: 'text-only', attachments: [] }) }
  })
})
