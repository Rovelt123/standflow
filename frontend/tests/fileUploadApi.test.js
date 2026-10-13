/* global File, FormData, sessionStorage, URL, Blob */
import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { portalRequest, uploadFiles, validateSelectedFiles, downloadFile } from '../src/pages/portal/portalApi.js'
import { getAuthToken } from '../src/pages/public/authApi.js'

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

//--------------------------------------------------------------

function downloadDom(t, failClick = false) {
  const calls = { blobs: [], appended: 0, clicked: 0, removed: 0, revoked: [], timers: [] }
  const anchor = {
    href: '', download: '',
    click() { calls.clicked++; if (failClick) throw new Error('Browser failure') },
    remove() { calls.removed++ },
  }
  const original = Object.getOwnPropertyDescriptor(globalThis, 'document')
  Object.defineProperty(globalThis, 'document', { configurable: true, value: {
    createElement: tag => { assert.equal(tag, 'a'); return anchor },
    body: { appendChild: element => { assert.equal(element, anchor); calls.appended++ } },
  } })
  t.after(() => { if (original) Object.defineProperty(globalThis, 'document', original); else delete globalThis.document })
  t.mock.method(URL, 'createObjectURL', blob => { calls.blobs.push(blob); return 'blob:download-fixture' })
  t.mock.method(URL, 'revokeObjectURL', url => calls.revoked.push(url))
  t.mock.method(globalThis, 'setTimeout', (callback, delay) => { calls.timers.push({ callback, delay }); return 0 })
  return { calls, anchor }
}

//--------------------------------------------------------------

test('authenticated downloads preserve bytes and original filenames for PDF and images', async t => {
  const { calls, anchor } = downloadDom(t)
  for (const extension of ['pdf', 'png', 'jpg', 'jpeg']) {
    const filename = `læse "original".${extension}`
    const content = new Blob(['original bytes'], { type: extension === 'pdf' ? 'application/pdf' : extension === 'png' ? 'image/png' : 'image/jpeg' })
    await downloadFile('file-id', filename, async (url, options) => {
      assert.equal(url, '/api/files/file-id')
      assert.equal(options.method, 'GET')
      assert.deepEqual(options.headers, { Authorization: `Bearer ${token}` })
      assert.equal(options.body, undefined)
      assert.equal(options.cache, 'no-store')
      return { status: 200, blob: async () => content }
    })
    assert.equal(anchor.download, filename)
    assert.equal(anchor.href, 'blob:download-fixture')
    assert.equal(anchor.target, undefined)
    assert.equal(await calls.blobs.at(-1).text(), 'original bytes')
  }
  assert.equal(calls.appended, 4)
  assert.equal(calls.clicked, 4)
  assert.equal(calls.removed, 4)
  assert.equal(calls.revoked.length, 0)
  calls.timers.forEach(timer => { assert.equal(timer.delay, 1000); timer.callback() })
  assert.equal(calls.revoked.length, 4)
})

//--------------------------------------------------------------

test('download failures never create a download or reveal technical server details', async t => {
  const { calls } = downloadDom(t)
  for (const status of [400, 403, 404, 500, 201, 204]) {
    await assert.rejects(downloadFile('file-id', 'original.pdf', async () => ({ status, text: async () => 'Internal path or database details' })),
      error => !error.message.includes('Internal'))
    assert.equal(getAuthToken(), token)
  }
  await assert.rejects(downloadFile('file-id', 'original.pdf', async () => { throw new Error('Network failure') }), /Forbindelsen/)
  await assert.rejects(downloadFile('file-id', 'original.pdf', async () => ({ status: 200, blob: async () => { throw new Error('Partial transfer') } })), /modtages/)
  assert.equal(calls.clicked, 0)
  assert.equal(calls.blobs.length, 0)
  await assert.rejects(downloadFile('file-id', 'original.pdf', async () => ({ status: 401 })), /session er udløbet/)
  assert.equal(getAuthToken(), null)
  await assert.rejects(downloadFile('file-id', 'original.pdf', async () => assert.fail('Unauthenticated request')), /Log ind igen/)
})

//--------------------------------------------------------------

test('download URL encodes the ID and a browser failure releases its temporary resources', async t => {
  const { calls } = downloadDom(t, true)
  await assert.rejects(downloadFile('../path', 'original.pdf', async (url, options) => {
    assert.equal(url, '/api/files/..%2Fpath')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    return { status: 200, blob: async () => new Blob(['content']) }
  }), /ikke startes/)
  assert.equal(calls.removed, 1)
  assert.deepEqual(calls.revoked, ['blob:download-fixture'])
  assert.equal(calls.timers.length, 0)
})
