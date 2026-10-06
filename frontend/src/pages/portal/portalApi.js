import { clearAuth, getAuthToken, replaceAuthToken } from '../public/authApi.js'

const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9393/api' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

export async function portalRequest(path, method = 'GET', body, fetchRequest = fetch) {
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
  if (![200, 201, 204].includes(response.status)) {
    if ([400, 403, 404, 409].includes(response.status)) {
      throw new Error(await response.text() || 'Handlingen kunne ikke gennemføres.')
    }
    throw new Error('Handlingen kunne ikke gennemføres. Prøv igen senere.')
  }
  if (response.status === 204) return null
  return response.json()
}

//--------------------------------------------------------------

export async function saveProfile(body, passwordOnly = false) {
  const result = await portalRequest(passwordOnly ? '/users/me/password' : '/users/me', 'PUT', body)
  replaceAuthToken(result.token)
  return result.user
}
