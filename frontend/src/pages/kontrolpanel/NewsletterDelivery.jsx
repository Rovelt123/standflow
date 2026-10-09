import { useEffect, useRef, useState } from 'react'
import { audiences, getNewsletterRecipients, newsletterPayload, sendNewsletter, standTypes } from './newsletterAdminApi.js'
import styles from './NewsletterPage.module.css'

export default function NewsletterDelivery({ templates, busy, setBusy }) {
  const [templateId, setTemplateId] = useState('')
  const [audience, setAudience] = useState('INDIVIDUAL')
  const [category, setCategory] = useState('A')
  const [recipients, setRecipients] = useState([])
  const [selectedIds, setSelectedIds] = useState([])
  const [manualIds, setManualIds] = useState('')
  const [search, setSearch] = useState('')
  const [loadingRecipients, setLoadingRecipients] = useState(true)
  const [recipientsError, setRecipientsError] = useState('')
  const [reload, setReload] = useState(0)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const pending = useRef(false)
  const template = templates.find(item => item.id === templateId)
  const audienceLabel = audiences.find(option => option.value === audience)?.label

  useEffect(() => {
    if (audience !== 'INDIVIDUAL') return
    let active = true
    getNewsletterRecipients().then(data => { if (active) setRecipients(data) })
      .catch(failure => { if (active) setRecipientsError(failure.message) })
      .finally(() => { if (active) setLoadingRecipients(false) })
    return () => { active = false }
  }, [audience, reload])

  //--------------------------------------------------------------

  async function send(event) {
    event.preventDefault()
    if (busy || pending.current) return
    setError('')
    setResult(null)
    let payload
    try {
      payload = newsletterPayload({ audience, templateId: template?.id, category,
        recipientIds: [...selectedIds, ...manualIds.split(/[\s,]+/)] })
    } catch (failure) { setError(failure.message); return }
    const detail = audience === 'CATEGORY' ? `Standtype: ${category}`
      : audience === 'INDIVIDUAL' ? `${payload.recipientIds.length} modtagere valgt` : ''
    if (!window.confirm(`Send nyhedsbrevet?\n\nSkabelon: ${template.name}\nModtagere: ${audienceLabel}\n${detail}\n\nDette sender rigtige e-mails. Modtagere uden marketingsamtykke springes over.`)) return
    pending.current = true
    setSending(true)
    setBusy(true)
    try {
      const delivery = await sendNewsletter(payload)
      setResult({ ...delivery, templateName: template.name, audienceLabel, detail })
    } catch (failure) {
      setError(`${failure.message} Leveringen kunne ikke bekræftes. Nogle mails kan være sendt. Kontrollér leveringen, før du sender igen; en ny udsendelse kan give dubletter.`)
    } finally {
      pending.current = false
      setSending(false)
      setBusy(false)
    }
  }

  const visibleRecipients = recipients.filter(recipient =>
    `${recipient.name} ${recipient.company}`.toLocaleLowerCase('da').includes(search.toLocaleLowerCase('da')))

  return <section className={styles.panel} aria-labelledby="delivery-heading">
    <h2 id="delivery-heading">Send nyhedsbrev</h2>
    <p>Modtagere uden marketingsamtykke springes over. Alle ansøgere omfatter brugere med en ansøgning.</p>
    <form onSubmit={send}>
      <fieldset className={styles.form} disabled={busy}>
        <label className={styles.field}>Skabelon til udsendelse
          <select value={template?.id || ''} onChange={event => setTemplateId(event.target.value)} required>
            <option value="">Vælg en gemt skabelon</option>
            {templates.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}
          </select>
        </label>
        {template && <div className={styles.preview}>
          <h3>Gemt indhold til udsendelse</h3>
          <p><strong>{template.subject}</strong></p><pre>{template.body}</pre>
          <p className={styles.hint}>Pladsholdere udfyldes for hver modtager. Afmeldingslink tilføjes automatisk.</p>
        </div>}
        <label className={styles.field}>Modtagergruppe
          <select value={audience} onChange={event => {
            setAudience(event.target.value)
            setLoadingRecipients(true)
            setRecipientsError('')
          }}>
            {audiences.map(option => <option value={option.value} key={option.value}>{option.label}</option>)}
          </select>
        </label>
        {audience === 'CATEGORY' && <label className={styles.field}>Standtype (kategori)
          <select value={category} onChange={event => setCategory(event.target.value)}>
            {standTypes.map(type => <option key={type} value={type}>Type {type}</option>)}
          </select>
        </label>}
        {audience === 'INDIVIDUAL' && <div className={styles.form}>
          <p className={styles.hint}>Vælg blandt dine eksisterende samtaler, eller indtast kendte bruger-id'er nedenfor. Listen omfatter kun brugere, du har en samtale med.</p>
          <label className={styles.field}>Søg navn eller virksomhed<input type="search" value={search} onChange={event => setSearch(event.target.value)} /></label>
          {loadingRecipients && <p role="status">Henter modtagere…</p>}
          {recipientsError && <div><p role="alert" className={styles.error}>{recipientsError}</p>
            <button type="button" className={styles.secondary} disabled={loadingRecipients} onClick={() => { setLoadingRecipients(true); setRecipientsError(''); setReload(value => value + 1) }}>Hent modtagere igen</button></div>}
          {!loadingRecipients && !recipientsError && <div className={styles.recipients}>
            {visibleRecipients.length === 0 && <p>Ingen modtagere fundet i samtalerne.</p>}
            {visibleRecipients.map(recipient => <label className={styles.checkbox} key={recipient.id}>
              <input type="checkbox" checked={selectedIds.includes(recipient.id)} onChange={event => setSelectedIds(current =>
                event.target.checked ? [...current, recipient.id] : current.filter(id => id !== recipient.id))} />
              <span>{recipient.name} · {recipient.company}</span>
            </label>)}
          </div>}
          <p className={styles.hint}>{selectedIds.length} valgt fra samtaler (også valg skjult af søgningen).</p>
          <label className={styles.field}>Andre modtagere – kendte bruger-id'er (valgfrit)
            <textarea value={manualIds} onChange={event => setManualIds(event.target.value)} rows={3} placeholder="Ét bruger-id (UUID) pr. linje eller adskilt med komma" />
          </label>
        </div>}
        <button type="submit" className={styles.send} disabled={!template}>{sending ? 'Sender…' : 'Bekræft og send nyhedsbrev'}</button>
      </fieldset>
    </form>
    {sending && <p role="status">Udsendelsen behandles. Vent på leveringstallene.</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {result && <div className={result.failedToSend > 0 ? styles.warning : styles.result} role="status">
      <h3>{result.failedToSend > 0 ? 'Udsendelsen er afsluttet med leveringsfejl' : 'Udsendelsen er behandlet'}</h3>
      <p>{result.templateName} · {result.audienceLabel} {result.detail}</p>
      <ul><li><strong>{result.sentTo}</strong> mails sendt</li>
        <li><strong>{result.skippedNoConsent}</strong> sprunget over uden marketingsamtykke</li>
        <li><strong>{result.failedToSend}</strong> mails kunne ikke sendes</li></ul>
    </div>}
  </section>
}
