// Scores matching words; company matches take precedence over customer-note matches.
export function applicationSearchScore(application, query) {
  const text = query.trim().toLocaleLowerCase('da-DK')
  if (!text) return 0
  const words = text.split(/\s+/)
  const company = (application.company || '').toLocaleLowerCase('da-DK')
  const note = (application.customerNote || '').toLocaleLowerCase('da-DK')
  const noteWords = new Set(note.split(/[^\p{L}\p{N}]+/u))
  const other = [application.contact, application.email, application.cvr, application.city]
    .map(value => String(value ?? '').toLocaleLowerCase('da-DK')).join(' ')
  if (!words.every(word => company.includes(word) || note.includes(word) || other.includes(word))) return -1
  let score = company === text ? 100000 : company.startsWith(text) ? 50000 : 0
  for (const word of words) {
    if (company.includes(word)) score += 10000
    if (other.includes(word)) score += 20
    if (noteWords.has(word)) score += 10
    else if (note.includes(word)) score += 5
  }
  return score
}
