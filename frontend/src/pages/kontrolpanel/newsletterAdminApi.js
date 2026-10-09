import { portalRequest } from '../portal/portalApi.js'

export const audiences = [
  { value: 'INDIVIDUAL', label: 'Udvalgte modtagere' },
  { value: 'CATEGORY', label: 'Samme standtype' },
  { value: 'NEW_STALLHOLDERS', label: 'Nye stadeholdere' },
  { value: 'PREVIOUS_YEAR_STALLHOLDERS', label: 'Forrige års stadeholdere' },
  { value: 'ALL_PREVIOUS_STALLHOLDERS', label: 'Alle tidligere stadeholdere' },
  { value: 'ALL_APPLICANTS', label: 'Alle ansøgere' },
]
export const standTypes = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const adminErrors = { 403: 'Du har ikke adgang til at administrere nyhedsbreve.' }

//--------------------------------------------------------------

function request(path, method, body, fetchRequest, expectedStatus = 200) {
  return portalRequest(path, method, body, fetchRequest, { expectedStatus, errorMessages: adminErrors })
}

//--------------------------------------------------------------

function templateResult(value) {
  if (!value || !['id', 'name', 'subject', 'body'].every(key => typeof value[key] === 'string')) {
    throw new Error('Skabelonen kunne ikke læses. Genindlæs skabelonerne.')
  }
  return value
}

//--------------------------------------------------------------

export async function getTemplates(fetchRequest = fetch) {
  const result = await request('/template', 'GET', undefined, fetchRequest)
  if (!Array.isArray(result?.data?.data)) throw new Error('Skabelonerne kunne ikke læses.')
  return result.data.data.map(templateResult)
}

//--------------------------------------------------------------

export async function getTemplate(id, fetchRequest = fetch) {
  const result = await request(`/template/${encodeURIComponent(id)}`, 'GET', undefined, fetchRequest)
  return templateResult(result?.data?.data)
}

//--------------------------------------------------------------

export async function saveTemplate(id, { name, subject, body }, fetchRequest = fetch) {
  const result = await request(id ? `/template/${encodeURIComponent(id)}` : '/template',
    id ? 'PUT' : 'POST', { name, subject, body }, fetchRequest, id ? 200 : 201)
  return templateResult(result)
}

//--------------------------------------------------------------

export function deleteTemplate(id, fetchRequest = fetch) {
  return request(`/template/${encodeURIComponent(id)}`, 'DELETE', undefined, fetchRequest, 204)
}

//--------------------------------------------------------------

export async function validateTemplate({ subject, body }, fetchRequest = fetch) {
  const result = await request('/template/validate', 'POST', { subject, body }, fetchRequest)
  if (typeof result?.valid !== 'boolean' || !Array.isArray(result.unknownVariables)) {
    throw new Error('Valideringsresultatet kunne ikke læses.')
  }
  return result
}

//--------------------------------------------------------------

export async function renderTemplate(id, { firstName, lastName, company, email }, fetchRequest = fetch) {
  const result = await request(`/template/${encodeURIComponent(id)}/render`, 'POST',
    { firstName, lastName, company, email }, fetchRequest)
  if (typeof result?.subject !== 'string' || typeof result?.body !== 'string' || !Array.isArray(result.unknownVariables)) {
    throw new Error('Forhåndsvisningen kunne ikke læses.')
  }
  return result
}

//--------------------------------------------------------------

export async function getNewsletterRecipients(fetchRequest = fetch) {
  const result = await request('/messages/threads?sort=name', 'GET', undefined, fetchRequest)
  if (!Array.isArray(result)) throw new Error('Modtagerne kunne ikke læses.')
  return [...new Map(result.map(thread => [thread.customerId, {
    id: thread.customerId, name: thread.customerName, company: thread.company,
  }])).values()]
}

//--------------------------------------------------------------

export function newsletterPayload({ audience, templateId, recipientIds = [], category }) {
  if (!templateId) throw new Error('Vælg en skabelon, før du sender.')
  if (!audiences.some(option => option.value === audience)) throw new Error('Vælg en modtagergruppe.')
  const payload = { audience, templateId }
  if (audience === 'INDIVIDUAL') {
    const ids = [...new Set(recipientIds.map(id => id.trim().toLowerCase()).filter(Boolean))]
    if (!ids.length) throw new Error('Vælg mindst én modtager.')
    if (ids.some(id => !uuidPattern.test(id))) throw new Error('Modtager-id skal være et gyldigt bruger-id (UUID).')
    payload.recipientIds = ids
  }
  if (audience === 'CATEGORY') {
    if (!standTypes.includes(category)) throw new Error('Vælg en standtype fra A til H.')
    payload.category = category
  }
  return payload
}

//--------------------------------------------------------------

export async function sendNewsletter(selection, fetchRequest = fetch) {
  const payload = newsletterPayload(selection)
  const result = await request('/newsletter', 'POST', payload, fetchRequest, 202)
  if (!['sentTo', 'skippedNoConsent', 'failedToSend'].every(key => Number.isInteger(result?.[key]) && result[key] >= 0)) {
    throw new Error('Serverens leveringstal kunne ikke læses.')
  }
  return result
}
