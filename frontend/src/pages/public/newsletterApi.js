const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9393/api' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

// Public afmelding fra et link i en nyhedsbrevmail. Kræver ikke login; token identificerer modtageren.
export async function unsubscribeFromNewsletter(token, fetchRequest = fetch) {
  if (!token) throw new Error('Afmeldingslinket mangler et token.')
  let response
  try {
    response = await fetchRequest(`${apiBaseUrl}/newsletter/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    })
  } catch {
    throw new Error('Forbindelsen blev afbrudt. Prøv igen senere.')
  }
  if (response.status !== 200) {
    if ([400, 404].includes(response.status)) {
      throw new Error((await response.text()) || 'Afmeldingslinket er ugyldigt eller udløbet.')
    }
    throw new Error('Afmeldingen kunne ikke gennemføres. Prøv igen senere.')
  }
  return response.json()
}
