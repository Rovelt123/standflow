import { clearAuth, getAuthToken } from '../public/authApi.js'

const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9595/api' : '/api')).replace(/\/+$/, '')

export async function submitApplication(application, fetchRequest = fetch) {
  const token = getAuthToken()
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}/applications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(application),
    })
  } catch {
    throw new Error('Forbindelsen blev afbrudt. Vi kan ikke bekræfte, om ansøgningen blev modtaget. Dine oplysninger er bevaret.')
  }

  if (response.status === 401 || response.status === 403) {
    clearAuth()
    throw new Error('Din session er udløbet. Log ind igen for at sende ansøgningen. Dine oplysninger er bevaret.')
  }
  if (response.status === 400 || response.status === 409) {
    const message = await response.text().catch(() => '')
    throw new Error(message || 'Ansøgningen kunne ikke sendes. Kontrollér oplysningerne, og prøv igen.')
  }
  if (response.status === 503) {
    throw new Error('Ansøgningsstatus kunne ikke hentes. Prøv igen senere. Dine oplysninger er bevaret.')
  }
  if (response.status !== 201) {
    throw new Error('Ansøgningen kunne ikke bekræftes. Prøv igen senere, eller kontakt arrangøren. Dine oplysninger er bevaret.')
  }

  try {
    const receipt = await response.json()
    if (!receipt || typeof receipt.id !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(receipt.id)
      || receipt.status !== 'PENDING'
      || typeof receipt.createdAt !== 'string'
      || !/^\d{4}-\d{2}-\d{2}$/.test(receipt.createdAt)) {
      throw new Error('Invalid receipt')
    }
    return receipt
  } catch {
    throw new Error('Serveren modtog indsendelsen, men kvitteringen kunne ikke læses. Kontakt arrangøren, før du sender igen.')
  }
}
