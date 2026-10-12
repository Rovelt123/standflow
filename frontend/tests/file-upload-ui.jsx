/* global window, document, Event, HTMLInputElement, HTMLAnchorElement, sessionStorage, localStorage, btoa, URL, File, FormData, Blob */
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import CustomerChat from '../src/pages/portal/CustomerChat.jsx'
import MessagesPage from '../src/pages/kontrolpanel/MessagesPage.jsx'
import portalStyles from '../src/pages/portal/PortalPage.module.css'
import '../src/index.css'

// Open /tests/file-upload-ui.html on Vite. All HTTP responses are isolated fixtures.
globalThis.IS_REACT_ACT_ENVIRONMENT = true
const root = createRoot(document.getElementById('root'))
const results = document.getElementById('results')
const customerId = '12345678-1234-1234-1234-123456789abc'
const otherId = '22345678-1234-1234-1234-123456789abc'
const adminId = '32345678-1234-1234-1234-123456789abc'
const token = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`
const reports = []
let calls = []
let messages = []
let mode = 'success'
let resolveUpload
let adminView = false
let resolveDownload
let completedDownloads = []
const downloadBlobs = new Map()
const createObjectURL = URL.createObjectURL.bind(URL)
const revokeObjectURL = URL.revokeObjectURL.bind(URL)
const anchorClick = HTMLAnchorElement.prototype.click
URL.createObjectURL = blob => {
  const url = createObjectURL(blob)
  downloadBlobs.set(url, blob)
  return url
}
URL.revokeObjectURL = url => { downloadBlobs.delete(url); revokeObjectURL(url) }
HTMLAnchorElement.prototype.click = function () {
  if (this.download) {
    completedDownloads.push({ name: this.download, url: this.href, blob: downloadBlobs.get(this.href), target: this.target })
    const requested = new URL(window.location.href).searchParams.get('nativeDownload')
    if (requested === '1' || requested === `${adminView ? 'admin' : 'customer'}:${this.download}`) anchorClick.call(this)
  } else anchorClick.call(this)
}

//--------------------------------------------------------------

function response(body, status = 200) {
  return { status, json: async () => globalThis.structuredClone(body), text: async () => typeof body === 'string' ? body : JSON.stringify(body) }
}

//--------------------------------------------------------------

globalThis.fetch = async (url, options = {}) => {
  const path = new URL(url, window.location.origin).pathname
  const multipart = options.body instanceof FormData
  const call = { path, method: options.method || 'GET', headers: options.headers,
    body: multipart ? options.body.getAll('files') : options.body ? JSON.parse(options.body) : undefined }
  calls.push(call)
  if (path.startsWith('/api/files/') && call.method === 'GET') {
    if (mode === 'download-failure') return response('Filen findes ikke.', 404)
    const binary = { status: 200, blob: async () => new Blob(['valid fixture'], { type: 'application/pdf' }) }
    if (mode === 'download-deferred') return new Promise(resolve => { resolveDownload = () => resolve(binary) })
    return binary
  }
  if (path === '/api/messages/threads') return response([
    { customerId, customerName: 'Alice', company: 'Test Company', subject: 'Documents', lastMessage: '', lastMessageAt: '2026-10-12T10:00:00' },
    { customerId: otherId, customerName: 'Bob', company: 'Other Company', subject: 'Other', lastMessage: '', lastMessageAt: '2026-10-12T10:00:00' },
  ])
  if (call.method === 'GET' && path.startsWith('/api/messages/')) return response(path.includes(otherId) ? [] : messages)
  if (call.method === 'PATCH') return response(null, 204)
  if (path === '/api/files') {
    if (mode === 'upload-failure') return response('Filens indhold er ugyldigt.', 415)
    const metadata = { files: call.body.map((file, index) => ({ id: `file-${index}`, originalName: file.name,
      contentType: 'application/pdf', size: file.size, uploadedAt: '2026-10-12T10:00:00Z' })) }
    if (mode === 'deferred') return new Promise(resolve => { resolveUpload = () => resolve(response(metadata, 201)) })
    return response(metadata, 201)
  }
  if (call.method === 'POST' && path.startsWith('/api/messages')) {
    if (mode === 'message-failure') return response('Beskeden kunne ikke gemmes.', 500)
    const uploaded = calls.filter(current => current.path === '/api/files').at(-1)?.body || []
    const saved = { id: `message-${messages.length}`, senderId: adminView ? adminId : customerId,
      subject: call.body.subject || 'Documents', body: call.body.body, createdAt: '2026-10-12T10:00:00',
      attachments: (call.body.attachmentIds || []).map((id, index) => ({ id, originalName: uploaded[index].name })) }
    messages.push(saved)
    return response(saved, 201)
  }
  throw new Error(`Unexpected request ${call.method} ${path}`)
}

//--------------------------------------------------------------

function check(condition, message) {
  if (!condition) throw new Error(message)
  reports.push(`PASS ${message}`)
  results.textContent = reports.join('\n')
}

//--------------------------------------------------------------

async function click(element) {
  if (!element) throw new Error('Missing clickable element')
  await act(async () => element.click())
}

//--------------------------------------------------------------

async function input(element, value) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, value)
    element.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

//--------------------------------------------------------------

async function select(files) {
  const picker = document.querySelector('input[type="file"]')
  await act(async () => {
    Object.defineProperty(picker, 'files', { configurable: true, value: files })
    picker.dispatchEvent(new Event('change', { bubbles: true }))
  })
}

//--------------------------------------------------------------

async function submit() {
  await act(async () => document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
}

//--------------------------------------------------------------

const uploadCalls = () => calls.filter(call => call.path === '/api/files')
const messageCalls = () => calls.filter(call => call.method === 'POST' && call.path.startsWith('/api/messages'))
const selectedNames = () => [...document.querySelectorAll('[aria-label="Valgte filer"] li span')].map(node => node.textContent)

//--------------------------------------------------------------

async function verify(admin) {
  adminView = admin
  const label = admin ? 'Admin' : 'Customer'
  await act(async () => root.render(null))
  calls = []
  messages = []
  completedDownloads = []
  mode = 'success'
  localStorage.removeItem('standflow.auth.token')
  sessionStorage.setItem('standflow.auth.token', token)
  await act(async () => root.render(<MemoryRouter>{admin ? <MessagesPage />
    : <main className={portalStyles.page}><CustomerChat user={{ id: customerId }} /></main>}</MemoryRouter>))
  if (admin) await click([...document.querySelectorAll('button')].find(button => button.textContent.includes('Alice')))
  const attach = document.querySelector('[aria-label="Vedhæft filer"]')
  const send = document.querySelector(admin ? '[aria-label="Send svar"]' : '[aria-label="Send besked"]')
  check(attach && send && attach.nextElementSibling.tagName === 'DIALOG' && attach.parentElement === send.parentElement,
    `${label}: upload control is beside Send`)
  await click(attach)
  check(document.querySelector('dialog').open, `${label}: upload dialog opens`)
  const text = document.querySelector('dialog').textContent
  check(['PDF', 'PNG', 'JPG', 'JPEG', '1 MiB', '3 filer'].every(value => text.includes(value)), `${label}: dialog explains all limits`)
  const picker = document.querySelector('input[type="file"]')
  check(picker.multiple && picker.accept === '.pdf,.png,.jpg,.jpeg', `${label}: picker permits three simultaneous files`)
  let pickerClicked = false
  picker.addEventListener('click', event => { pickerClicked = true; event.preventDefault() }, { once: true })
  await click([...document.querySelectorAll('dialog button')].find(button => button.textContent === 'Vælg filer'))
  check(pickerClicked, `${label}: picker button invokes the file input`)
  const files = ['a.pdf', 'b.png', 'c.jpeg'].map(name => new File(['valid fixture'], name))
  await select(files)
  check(selectedNames().length === 3 && !document.querySelector('dialog').open, `${label}: all three selected filenames appear`)
  await click(document.querySelector('[aria-label="Fjern b.png"]'))
  check(selectedNames().join(',') === 'a.pdf,c.jpeg', `${label}: selected attachment can be removed`)
  await select([...files, new File(['x'], 'fourth.pdf')])
  check(selectedNames().length === 2 && document.querySelector('[role="alert"]').textContent.includes('1 og 3'), `${label}: four-file selection rejected`)
  await select([new File([new Uint8Array(1048577)], 'large.pdf')])
  check(document.querySelector('[role="alert"]').textContent.includes('1 MiB'), `${label}: oversized selection rejected`)
  await select([new File(['x'], 'wrong.txt')])
  check(document.querySelector('[role="alert"]').textContent.includes('PDF'), `${label}: unsupported selection rejected`)
  await select(files)
  if (!admin) await input(document.querySelector('[aria-label="Emne"]'), 'Documents')
  await input(document.querySelector('[placeholder="Indtast besked"]'), 'Attached files')
  mode = 'upload-failure'
  await submit()
  check(uploadCalls().length === 1 && messageCalls().length === 0 && selectedNames().length === 3,
    `${label}: failed upload keeps selection and sends no message`)
  mode = 'deferred'
  await submit()
  check(document.querySelector('[role="status"]').textContent.includes('Uploader') && send.disabled && attach.disabled,
    `${label}: upload has a loading state and disabled controls`)
  await submit()
  check(uploadCalls().length === 2, `${label}: duplicate submit cannot duplicate upload`)
  mode = 'message-failure'
  await act(async () => resolveUpload())
  check(messageCalls().length === 1 && selectedNames().length === 3, `${label}: failed message retains pending uploads`)
  mode = 'success'
  await submit()
  check(uploadCalls().length === 2 && messages.length === 1 && selectedNames().length === 0,
    `${label}: controlled retry reuses upload IDs and creates one message`)
  const sent = document.querySelector('[aria-label="Vedhæftede filer"]')
  check(sent?.querySelectorAll('li button').length === 3 && !sent.querySelector('img,iframe'), `${label}: attachment names are download controls without previews`)
  check(document.querySelectorAll('[aria-label="Vedhæftede filer"]').length === 1, `${label}: only one attachment message is rendered`)
  check(uploadCalls().every(call => call.body.length === 3 && !call.headers['Content-Type']), `${label}: three files use one browser multipart request`)
  const links = [...sent.querySelectorAll('button')]
  check(links.every(link => window.getComputedStyle(link).color === 'rgb(37, 99, 235)'
    && window.getComputedStyle(link).textDecorationLine.includes('underline')), `${label}: full filenames are blue underlined controls`)
  const messagesBeforeDownload = messageCalls().length
  mode = 'download-failure'
  await click(links[0])
  check(sent.querySelector('[role="alert"]').textContent.includes('ikke tilgængelig') && completedDownloads.length === 0,
    `${label}: failed download displays an error and starts no download`)
  mode = 'download-deferred'
  await click(links[0])
  check(links[0].disabled && links[0].getAttribute('aria-busy') === 'true'
    && sent.querySelector('[role="status"]').textContent.includes('Forbereder'), `${label}: download preparation has a loading state`)
  const requestsBeforeRepeat = calls.filter(call => call.path.startsWith('/api/files/')).length
  await click(links[0])
  check(calls.filter(call => call.path.startsWith('/api/files/')).length === requestsBeforeRepeat, `${label}: pending download rejects repeated clicks`)
  mode = 'success'
  await act(async () => resolveDownload())
  await click(links[1])
  await click(links[2])
  check(completedDownloads.map(download => download.name).join(',') === 'a.pdf,b.png,c.jpeg'
    && completedDownloads.every(download => download.url.startsWith('blob:') && !download.target),
    `${label}: each attachment starts its own download using the original filename`)
  check((await Promise.all(completedDownloads.map(download => download.blob.text()))).every(content => content === 'valid fixture'),
    `${label}: browser download receives the original content`)
  const downloadRequests = calls.filter(call => call.path.startsWith('/api/files/'))
  check(downloadRequests.every(call => call.headers.Authorization === `Bearer ${token}` && call.body === undefined)
    && downloadRequests.at(-1).path.endsWith('/file-2'), `${label}: download requests include the file ID and JWT`)
  check(messageCalls().length === messagesBeforeDownload && messages.length === 1, `${label}: clicking attachments never sends chat messages`)
  await input(document.querySelector('[placeholder="Indtast besked"]'), 'Text only')
  await submit()
  check(messages.length === 2 && uploadCalls().length === 2 && !messageCalls().at(-1).body.attachmentIds,
    `${label}: text-only sending remains unchanged`)
  if (admin) {
    await select(files)
    await click([...document.querySelectorAll('button')].find(button => button.textContent.includes('Bob')))
    check(selectedNames().length === 0, 'Admin: switching conversations clears file selection')
    await click([...document.querySelectorAll('button')].find(button => button.textContent.includes('Alice')))
  }
}

//--------------------------------------------------------------

async function run() {
  await verify(false)
  if (new URL(window.location.href).searchParams.get('view') !== 'customer') await verify(true)
  results.textContent = `${reports.length} browser checks passed\n${reports.join('\n')}`
  if (new URL(window.location.href).searchParams.has('screenshot')) results.style.display = 'none'
}

run().catch(error => { results.textContent = `FAILED: ${error.message}\n${reports.join('\n')}` })
