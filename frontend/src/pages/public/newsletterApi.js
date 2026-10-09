const apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV ? 'http://localhost:9393/api' : '/api')).replace(/\/+$/, '')

//--------------------------------------------------------------

// Public afmelding fra et link i en nyhedsbrevmail. Kræver ikke login; token identificerer modtageren.
export async function unsubscribeFromNewsletter(token, fetchRequest = fetch) {
  if (typeof token !== 'string' || !token.trim()) throw new Error('Afmeldingslinket er ufuldstændigt. Brug linket i dit nyhedsbrev.')
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
      throw new Error('Afmeldingslinket er ugyldigt, udløbet eller allerede brugt. Brug linket i dit seneste nyhedsbrev, eller ændr samtykke i din brugerportal.')
    }
    throw new Error('Afmeldingen kunne ikke gennemføres. Prøv igen senere.')
  }
  try {
    const result = await response.json()
    if (result?.marketingConsent !== false) throw new Error('Invalid consent')
    return result
  } catch {
    throw new Error('Afmeldingen kunne ikke bekræftes. Kontrollér dit samtykke i brugerportalen.')
  }
}
