/* global window, document, Event, HTMLInputElement, HTMLTextAreaElement, HTMLSelectElement, sessionStorage, localStorage, btoa, URL */
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AccessPage from '../src/pages/public/AccessPage.jsx'
import UnsubscribePage from '../src/pages/public/UnsubscribePage.jsx'
import ProfileSettings from '../src/pages/portal/ProfileSettings.jsx'
import PortalPage from '../src/pages/portal/PortalPage.jsx'
import NewsletterPage from '../src/pages/kontrolpanel/NewsletterPage.jsx'
import MessagesRedirect from '../src/pages/public/MessagesRedirect.jsx'
import RequireAdmin from '../src/components/admin/RequireAdmin.jsx'
import '../src/index.css'

// Open /tests/newsletter-ui.html on the Vite dev server. Every fetch is mocked;
// unexpected requests fail and no backend or SMTP service is contacted.
globalThis.IS_REACT_ACT_ENVIRONMENT = true
const root = createRoot(document.getElementById('root'))
const results = document.getElementById('results')
const id = '12345678-1234-1234-1234-123456789abc'
const token = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`
const user = { id, email: 'ola@example.com', firstname: 'Ola', emailNotifications: true, roles: ['USER'] }
let template = { id, name: 'Nyheder', subject: 'Hej <Firstname>', body: 'Nyt til <Company>' }
let handler
let calls = []
let reports = []
let confirmation = false
let confirmationText = ''
globalThis.fetch = async (url, options = {}) => {
  const call = { path: new URL(url, window.location.origin).pathname, method: options.method || 'GET',
    body: options.body ? JSON.parse(options.body) : undefined }
  calls.push(call)
  return handler(call)
}
window.confirm = message => { confirmationText = message; return confirmation }

//--------------------------------------------------------------

function response(body, status = 200) {
  return { status, json: async () => body, text: async () => typeof body === 'string' ? body : JSON.stringify(body) }
}

//--------------------------------------------------------------

function check(condition, message) {
  if (!condition) throw new Error(message)
  reports.push(`PASS ${message}`)
  results.textContent = reports.join('\n')
}

//--------------------------------------------------------------

async function mount(component, route = '/') {
  await act(async () => root.render(null))
  calls = []
  localStorage.removeItem('standflow.auth.token')
  sessionStorage.setItem('standflow.auth.token', token)
  await act(async () => root.render(<MemoryRouter initialEntries={[route]}>{component}</MemoryRouter>))
}

//--------------------------------------------------------------

function button(text) {
  return [...document.querySelectorAll('button')].find(element => element.textContent === text)
}

//--------------------------------------------------------------

function field(text) {
  return [...document.querySelectorAll('label')].find(element => element.textContent.startsWith(text))?.querySelector('input,textarea,select')
}

//--------------------------------------------------------------

async function click(element) {
  if (!element) throw new Error('Missing clickable element')
  await act(async () => element.click())
}

//--------------------------------------------------------------

async function input(element, value) {
  const prototype = element.tagName === 'SELECT' ? HTMLSelectElement.prototype
    : element.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
  await act(async () => {
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value)
    element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
  })
}

//--------------------------------------------------------------

async function submit(form) {
  await act(async () => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
}

//--------------------------------------------------------------

async function run() {
  handler = call => {
    if (call.path === '/api/users/auth/register') return response({ data: { token } }, 201)
    if (call.path === '/api/users/me') return response(user)
    throw new Error(`Unexpected request: ${call.path}`)
  }
  await mount(<AccessPage register />)
  const marketing = document.querySelector('[name="acceptMarketing"]')
  check(!marketing.checked && !marketing.required, 'Registration marketing consent is optional and unchecked')
  await input(document.querySelector('[name="password"]'), 'Password!')
  await input(document.querySelector('[name="confirmPassword"]'), 'different')
  await submit(document.querySelector('form'))
  check(calls.length === 0 && document.body.textContent.includes('Adgangskoderne skal være ens.'), 'Existing password confirmation still blocks registration')
  await input(document.querySelector('[name="confirmPassword"]'), 'Password!')
  await submit(document.querySelector('form'))
  check(calls.find(call => call.method === 'POST').body.acceptMarketing === false, 'Registration sends false without marketing consent')
  await click(marketing)
  await submit(document.querySelector('form'))
  check(calls.filter(call => call.method === 'POST').at(-1).body.acceptMarketing === true, 'Checked consent sends true')

  let resolveConsent
  handler = () => new Promise(resolve => { resolveConsent = resolve })
  await mount(<ProfileSettings user={user} onUpdate={() => {}} />)
  check(!field('Jeg vil modtage marketing'), 'Settings show no false saved consent while loading')
  await act(async () => resolveConsent(response({ marketingConsent: false })))
  handler = () => response({ marketingConsent: true })
  await click(field('Jeg vil modtage marketing'))
  check(field('Jeg vil modtage marketing').checked, 'Confirmed opt-in is displayed')
  handler = () => response('Request failed', 500)
  await click(field('Jeg vil modtage marketing'))
  check(field('Jeg vil modtage marketing').checked, 'Failed opt-out preserves the last confirmed consent')
  handler = () => response({ marketingConsent: false })
  await click(button('Afmeld marketing'))
  check(!field('Jeg vil modtage marketing').checked, 'Confirmed account unsubscribe is displayed')

  handler = call => {
    if (call.path === '/api/users/me') return response({ ...user, roles: ['ADMIN'] })
    if (call.path === '/api/users/me/consent') return response({ marketingConsent: false })
    throw new Error(`Unexpected request: ${call.path}`)
  }
  await mount(<PortalPage />, '/brugerportal?tab=settings')
  check(Boolean(field('Jeg vil modtage marketing')) && !calls.some(call => call.path.includes('/applications')),
    'Admin preferences load without requiring access to customer applications')

  handler = () => response({ marketingConsent: false })
  await mount(<UnsubscribePage />, '/unsubscribe')
  check(calls.length === 0 && !button('Ja, afmeld mig'), 'Missing token blocks unsubscribe')
  await mount(<UnsubscribePage />, '/unsubscribe?token=test-token')
  check(calls.length === 0 && !document.body.textContent.includes('test-token'), 'URL token is hidden and opening the page makes no request')
  let resolveUnsubscribe
  handler = () => new Promise(resolve => { resolveUnsubscribe = resolve })
  const unsubscribe = button('Ja, afmeld mig')
  await click(unsubscribe)
  await click(unsubscribe)
  check(calls.length === 1 && unsubscribe.disabled, 'Unsubscribe prevents duplicate requests while pending')
  await act(async () => resolveUnsubscribe(response({ marketingConsent: false })))
  check(document.body.textContent.includes('Du er nu afmeldt'), 'Confirmed token unsubscribe shows success')
  handler = () => response('Invalid token', 400)
  await mount(<UnsubscribePage />, '/unsubscribe?token=invalid-token')
  await click(button('Ja, afmeld mig'))
  check(document.body.textContent.includes('allerede brugt'), 'Invalid, expired or consumed token shows appropriate feedback')

  let resolveDelivery
  handler = call => {
    if (call.path === '/api/template' && call.method === 'GET') return response({ data: { data: [template] } })
    if (call.path === `/api/template/${id}` && call.method === 'GET') return response({ data: { data: template } })
    if (call.path === '/api/messages/threads') return response([{ customerId: id, customerName: 'Ola Nordmann', company: 'Nord' }])
    if (call.path === '/api/template/validate') return response({ valid: false, unknownVariables: ['<Unknown>'] })
    if (call.path.endsWith('/render')) return response({ subject: 'Hej Ola', body: 'Backend preview', unknownVariables: [] })
    if (call.method === 'PUT' || (call.path === '/api/template' && call.method === 'POST')) {
      template = { id, ...call.body }
      return response(template, call.method === 'POST' ? 201 : 200)
    }
    if (call.method === 'DELETE') return response(null, 204)
    if (call.path === '/api/newsletter') return new Promise(resolve => { resolveDelivery = resolve })
    throw new Error(`Unexpected request: ${call.path}`)
  }
  await mount(<NewsletterPage />)
  await input(field('Vælg skabelon til redigering'), id)
  check(field('Skabelonnavn').value === template.name, 'Template list and detail load existing content')
  await click(button('Valider pladsholdere'))
  check(document.body.textContent.includes('Ukendte pladsholdere: <Unknown>'), 'Backend placeholder errors are visible')
  await click(button('Forhåndsvis'))
  check(document.body.textContent.includes('Backend preview'), 'Preview displays the backend render response')
  await input(field('Emne'), 'Changed <Firstname>')
  check(button('Forhåndsvis').disabled, 'Unsaved edits cannot show a misleading saved preview')
  await click(button('Gem skabelon'))
  check(calls.some(call => call.method === 'PUT' && call.body.subject === 'Changed <Firstname>'), 'Template updates use PUT')
  await input(field('Skabelon til udsendelse'), id)
  await click(field('Ola Nordmann'))
  confirmation = false
  await click(button('Bekræft og send nyhedsbrev'))
  check(!calls.some(call => call.path === '/api/newsletter'), 'Canceling delivery confirmation sends nothing')
  check(confirmationText.includes(template.name) && confirmationText.includes('Udvalgte modtagere') && confirmationText.includes('rigtige e-mails'), 'Delivery confirmation names template, audience and real email side effect')
  confirmation = true
  const send = button('Bekræft og send nyhedsbrev')
  await click(send)
  await submit(send.form)
  check(calls.filter(call => call.path === '/api/newsletter').length === 1, 'Duplicate submission cannot send two newsletters')
  await act(async () => resolveDelivery(response({ sentTo: 2, skippedNoConsent: 3, failedToSend: 1 }, 202)))
  check(document.body.textContent.includes('afsluttet med leveringsfejl') && document.body.textContent.includes('3 sprunget over'), 'Partial delivery shows actual counts and failure wording')
  await click(button('Bekræft og send nyhedsbrev'))
  await act(async () => resolveDelivery(response('Failure', 500)))
  check(calls.filter(call => call.path === '/api/newsletter').length === 2 && document.body.textContent.includes('Nogle mails kan være sendt'), 'Failed delivery warns about uncertainty and does not retry')
  confirmation = false
  await click(button('Slet skabelon'))
  check(!calls.some(call => call.method === 'DELETE'), 'Canceling template deletion preserves the template')
  confirmation = true
  await click(button('Slet skabelon'))
  check(!field('Skabelon til udsendelse').value, 'Deleted template is removed from delivery selection')
  await input(field('Skabelonnavn'), 'New template')
  await input(field('Emne'), 'Hej')
  await input(field('Brødtekst'), 'Nyt fra StandFlow')
  await click(button('Gem skabelon'))
  check(calls.some(call => call.path === '/api/template' && call.method === 'POST'), 'New template uses POST and becomes available')

  handler = () => response(user)
  await mount(<Routes><Route path="/" element={<RequireAdmin><p>Admin secret</p></RequireAdmin>} />
    <Route path="/brugerportal" element={<p>User portal</p>} /></Routes>)
  check(!document.body.textContent.includes('Admin secret') && document.body.textContent.includes('User portal'), 'Normal users cannot access admin newsletter components')
  for (const admin of [false, true]) {
    handler = () => response({ ...user, roles: admin ? ['ADMIN'] : ['USER'] })
    await mount(<Routes><Route path="/messages" element={<MessagesRedirect />} />
      <Route path="/brugerportal" element={<p>Customer inbox</p>} />
      <Route path="/kontrolpanel/beskeder" element={<p>Admin inbox</p>} /></Routes>, '/messages')
    check(document.body.textContent.includes(admin ? 'Admin inbox' : 'Customer inbox'), `/messages opens the ${admin ? 'admin' : 'customer'} inbox`)
  }
  results.textContent = `${reports.length} browser checks passed\n${reports.join('\n')}`
}

run().catch(error => { results.textContent = `FAILED: ${error.message}\n${reports.join('\n')}` })
