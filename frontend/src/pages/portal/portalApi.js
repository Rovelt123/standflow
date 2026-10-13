import { clearAuth, getAuthToken, replaceAuthToken } from '../public/authApi.js'

const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://standflow.api.roneu.dk:9595' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

export async function portalRequest(path, method = 'GET', body, fetchRequest = fetch, { errorMessages = {}, expectedStatus } = {}) {
  const token = getAuthToken()
  if (!token) throw new Error('Log ind igen for at fortsætte.')
  let response
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData
  try {
    response = await fetchRequest(`${apiBaseUrl}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body && !multipart ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: multipart ? body : JSON.stringify(body) } : {}),
    })
  } catch {
    throw new Error('Forbindelsen blev afbrudt. Genindlæs oplysningerne for at se, om ændringen blev gemt.')
  }
  if (response.status === 401) {
    clearAuth()
    throw new Error('Din session er udløbet. Log ind igen.')
  }
  if (![200, 201, 202, 204].includes(response.status) || (expectedStatus && response.status !== expectedStatus)) {
    if (errorMessages[response.status]) throw new Error(errorMessages[response.status])
    if ([400, 403, 404, 409, 413, 415].includes(response.status)) {
      throw new Error(await response.text() || 'Handlingen kunne ikke gennemføres.')
    }
    throw new Error('Handlingen kunne ikke gennemføres. Prøv igen senere.')
  }
  if (response.status === 204) return null
  try {
    return await response.json()
  } catch {
    throw new Error('Serverens svar kunne ikke læses. Genindlæs oplysningerne, før du prøver igen.')
  }
}

//--------------------------------------------------------------

export function validateSelectedFiles(files) {
  if (files.length < 1 || files.length > 3) throw new Error('Vælg mellem 1 og 3 filer.')
  for (const file of files) {
    if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) throw new Error('Kun PDF, PNG, JPG og JPEG er tilladt.')
    if (file.size === 0) throw new Error('Tomme filer kan ikke uploades.')
    if (file.size > 1048576) throw new Error('Hver fil må højst være 1 MiB (1.048.576 bytes).')
  }
}

//--------------------------------------------------------------

export async function uploadFiles(files, fetchRequest = fetch) {
  validateSelectedFiles(files)
  const form = new FormData()
  files.forEach(file => form.append('files', file))
  const result = await portalRequest('/files', 'POST', form, fetchRequest)
  if (!Array.isArray(result?.files) || result.files.length !== files.length
    || result.files.some(file => typeof file?.id !== 'string')
    || new Set(result.files.map(file => file.id)).size !== files.length) {
    throw new Error('Serverens upload-svar kunne ikke læses. Beskeden er ikke sendt.')
  }
  return result.files
}

//--------------------------------------------------------------

export async function downloadFile(fileId, originalName, fetchRequest = fetch) {
  const token = getAuthToken()
  if (!token) throw new Error('Log ind igen for at fortsætte.')
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}/files/${encodeURIComponent(fileId)}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    })
  } catch {
    throw new Error('Forbindelsen blev afbrudt. Filen blev ikke downloadet.')
  }
  if (response.status === 401) {
    clearAuth()
    throw new Error('Din session er udløbet. Log ind igen.')
  }
  if (response.status === 400) throw new Error('Filens id er ugyldigt.')
  if ([403, 404].includes(response.status)) throw new Error('Filen findes ikke eller er ikke tilgængelig.')
  if (response.status !== 200) throw new Error('Filen kunne ikke downloades. Prøv igen senere.')
  let blob
  try {
    blob = await response.blob()
  } catch {
    throw new Error('Filen kunne ikke modtages. Prøv igen senere.')
  }
  let url
  let anchor
  let started = false
  try {
    url = URL.createObjectURL(blob)
    anchor = document.createElement('a')
    anchor.href = url
    anchor.download = originalName
    document.body.appendChild(anchor)
    anchor.click()
    started = true
  } catch {
    throw new Error('Download kunne ikke startes. Prøv igen senere.')
  } finally {
    anchor?.remove()
    if (url) {
      // Allow the browser to begin reading the Blob before releasing its URL.
      if (started) setTimeout(() => URL.revokeObjectURL(url), 1000)
      else URL.revokeObjectURL(url)
    }
  }
}

//--------------------------------------------------------------

export async function saveProfile(body, passwordOnly = false) {
  const result = await portalRequest(passwordOnly ? '/users/me/password' : '/users/me', 'PUT', body)
  replaceAuthToken(result.token)
  return result.user
}

//--------------------------------------------------------------

async function consentRequest(path, method, body, fetchRequest) {
  const result = await portalRequest(path, method, body, fetchRequest, {
    expectedStatus: 200,
    errorMessages: {
      400: 'Samtykket kunne ikke gemmes. Genindlæs oplysningerne, og prøv igen.',
      404: 'Din bruger kunne ikke findes. Log ind igen.',
    },
  })
  if (typeof result?.marketingConsent !== 'boolean') {
    throw new Error('Samtykket kunne ikke læses. Genindlæs oplysningerne, før du prøver igen.')
  }
  return result.marketingConsent
}

//--------------------------------------------------------------

export function getMarketingConsent(fetchRequest = fetch) {
  return consentRequest('/users/me/consent', 'GET', undefined, fetchRequest)
}

//--------------------------------------------------------------

export function updateMarketingConsent(marketingConsent, fetchRequest = fetch) {
  return consentRequest('/users/me/consent', 'PATCH', { marketingConsent }, fetchRequest)
}

//--------------------------------------------------------------

// Account settings use the authenticated endpoint; email links use the public token endpoint.
export function unsubscribeMarketing(fetchRequest = fetch) {
  return consentRequest('/users/me/unsubscribe', 'POST', undefined, fetchRequest)
}

//--------------------------------------------------------------

export async function deleteAccount(currentPassword, confirmDelete, fetchRequest = fetch) {
  if (typeof currentPassword !== 'string' || !currentPassword.trim() || confirmDelete !== true) {
    throw new Error('Indtast din nuværende adgangskode, og bekræft permanent sletning.')
  }
  await portalRequest('/users/me', 'DELETE', { currentPassword, confirmDelete: true }, fetchRequest, {
    expectedStatus: 204,
    errorMessages: {
      400: 'Kontoen kunne ikke slettes. Kontrollér din nuværende adgangskode og bekræftelsen.',
      404: 'Din bruger kunne ikke findes. Log ind igen.',
    },
  })
  clearAuth()
}
