const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9393/api' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

// Offentlig liste over accepterede stadeholdere, 10 pr. side. Kræver ikke login.
export async function fetchVendors(page = 1, fetchRequest = fetch) {
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}/vendors?page=${encodeURIComponent(page)}`)
  } catch {
    throw new Error('Forbindelsen blev afbrudt. Prøv igen senere.')
  }
  if (response.status !== 200) {
    throw new Error('Stadeholderne kunne ikke hentes lige nu. Prøv igen senere.')
  }
  try {
    return await response.json()
  } catch {
    throw new Error('Svaret fra serveren kunne ikke læses.')
  }
}
