import { useEffect, useMemo, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import { portalRequest } from '../portal/portalApi.js'
import shell from './KontrolpanelPage.module.css'
import styles from './NewsletterPage.module.css'

// Matches app.enums.NewsletterAudience in the backend. Order and keys must not change.
const AUDIENCES = [
  { value: 'INDIVIDUAL', label: 'Udvalgte modtagere' },
  { value: 'CATEGORY', label: 'Samme kategori (standtype)' },
  { value: 'NEW_STALLHOLDERS', label: 'Nye stadeholdere' },
  { value: 'PREVIOUS_YEAR_STALLHOLDERS', label: 'Forrige års stadeholdere' },
  { value: 'ALL_PREVIOUS_STALLHOLDERS', label: 'Alle tidligere stadeholdere' },
  { value: 'ALL_APPLICANTS', label: 'Alle ansøgere' },
]

// Backend CATEGORY filters by ApplicationStandType (A-H), so "kategori" er en standtype.
const STAND_TYPES = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

// GET /template answers {message, data:{data:[TemplateDTO]}} via BaseController. Unwrap defensively.
function extractTemplates(payload) {
  const inner = payload?.data?.data ?? payload?.data ?? payload
  return Array.isArray(inner) ? inner : []
}

export default function NewsletterPage() {
  const [templates, setTemplates] = useState([])
  const [templatesError, setTemplatesError] = useState('')
  const [loadingTemplates, setLoadingTemplates] = useState(true)

  const [audience, setAudience] = useState('ALL_APPLICANTS')
  const [templateId, setTemplateId] = useState('')
  const [category, setCategory] = useState('A')
  const [recipientIdsText, setRecipientIdsText] = useState('')

  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    let active = true
    setLoadingTemplates(true)
    setTemplatesError('')

    portalRequest('/template')
      .then((data) => {
        if (active) setTemplates(extractTemplates(data))
      })
      .catch((error) => {
        if (active) setTemplatesError(error.message)
      })
      .finally(() => {
        if (active) setLoadingTemplates(false)
      })

    return () => { active = false }
  }, [])

  const recipientIds = useMemo(
    () => recipientIdsText.split(/[\s,]+/).map((id) => id.trim()).filter(Boolean),
    [recipientIdsText],
  )

  const needsRecipients = audience === 'INDIVIDUAL'
  const needsCategory = audience === 'CATEGORY'

  function validationError() {
    if (!templateId) return 'Vælg en skabelon, før du sender.'
    if (needsRecipients && recipientIds.length === 0) {
      return 'Angiv mindst ét modtager-id for udvalgte modtagere.'
    }
    return ''
  }

  async function send(event) {
    event.preventDefault()
    if (sending) return

    const invalid = validationError()
    if (invalid) {
      setSendError(invalid)
      setResult(null)
      return
    }

    // Backend afviser ekstra modtagerfelter, så send kun dem den valgte audience bruger.
    const payload = { audience, templateId }
    if (needsRecipients) payload.recipientIds = recipientIds
    if (needsCategory) payload.category = category

    setSending(true)
    setSendError('')
    setResult(null)

    try {
      const data = await portalRequest('/newsletter', 'POST', payload)
      setResult(data)
    } catch (error) {
      setSendError(error.message)
    } finally {
      setSending(false)
    }
  }

  return (
    <div className={shell.page}>
      <AdminTopBar title="Nyhedsbrev" />
      <AdminSidebar />

      <main className={`${shell.main} ${styles.main}`}>
        <section className={styles.panel} aria-label="Send nyhedsbrev">
          <h1>NYHEDSBREV</h1>
          <p className={styles.intro}>
            Vælg modtagere og en skabelon. Variabler i skabelonen erstattes med den
            enkelte modtagers data, og alle markedsføringsmails får automatisk et
            afmeldingslink. Modtagere uden samtykke springes over.
          </p>

          <form className={styles.form} onSubmit={send}>
            <label className={styles.field}>
              <span>Modtagere</span>
              <select
                value={audience}
                onChange={(event) => {
                  setAudience(event.target.value)
                  setResult(null)
                  setSendError('')
                }}
              >
                {AUDIENCES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            {needsCategory && (
              <label className={styles.field}>
                <span>Standtype (kategori)</span>
                <select value={category} onChange={(event) => setCategory(event.target.value)}>
                  {STAND_TYPES.map((type) => (
                    <option key={type} value={type}>
                      Type {type}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {needsRecipients && (
              <label className={styles.field}>
                <span>Modtager-id&apos;er</span>
                <textarea
                  value={recipientIdsText}
                  onChange={(event) => setRecipientIdsText(event.target.value)}
                  placeholder="Ét bruger-id pr. linje (eller adskilt med komma)"
                  rows={4}
                />
                <small className={styles.hint}>
                  {recipientIds.length} modtager{recipientIds.length === 1 ? '' : 'e'} valgt.
                </small>
              </label>
            )}

            <label className={styles.field}>
              <span>Skabelon</span>
              <select
                value={templateId}
                onChange={(event) => setTemplateId(event.target.value)}
                disabled={loadingTemplates || templates.length === 0}
              >
                <option value="">
                  {loadingTemplates ? 'Henter skabeloner…' : 'Vælg en skabelon'}
                </option>
                {templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
              {templatesError && <small className={styles.error} role="alert">{templatesError}</small>}
              {!loadingTemplates && !templatesError && templates.length === 0 && (
                <small className={styles.hint}>
                  Ingen skabeloner endnu. Opret en skabelon først.
                </small>
              )}
            </label>

            <button type="submit" className={styles.send} disabled={sending || loadingTemplates}>
              {sending ? 'Sender…' : 'Send nyhedsbrev'}
            </button>
          </form>

          {sendError && <p className={styles.error} role="alert">{sendError}</p>}

          {result && (
            <div className={styles.result} role="status">
              <h2>Nyhedsbrevet er sat i gang</h2>
              <ul>
                <li><strong>{result.sentTo}</strong> sendt</li>
                <li><strong>{result.skippedNoConsent}</strong> sprunget over (intet samtykke)</li>
                <li><strong>{result.failedToSend}</strong> kunne ikke sendes</li>
              </ul>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
