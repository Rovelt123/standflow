import { clearAuth, getAuthToken, replaceAuthToken } from '../public/authApi.js'

const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9595/api' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

export async function portalRequest(path, method = 'GET', body, fetchRequest = fetch, { errorMessages = {}, expectedStatus } = {}) {
  const token = getAuthToken()
  if (!token) throw new Error('Log ind igen for at fortsætte.')
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
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
    if ([400, 403, 404, 409].includes(response.status)) {
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

// US5: unsubscribe requires login; the backend has no public unsubscribe-token endpoint.
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
