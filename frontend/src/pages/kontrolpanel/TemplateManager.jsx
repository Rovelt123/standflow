import { useRef, useState } from 'react'
import { deleteTemplate, getTemplate, renderTemplate, saveTemplate, validateTemplate } from './newsletterAdminApi.js'
import styles from './NewsletterPage.module.css'

const emptyDraft = { name: '', subject: '', body: '' }
const previewRecipient = { firstName: 'Ola', lastName: 'Nordmann', company: 'Nord Stand ApS', email: 'ola@example.com' }

export default function TemplateManager({ templates, busy, setBusy, onSave, onDelete }) {
  const [saved, setSaved] = useState(null)
  const [draft, setDraft] = useState(emptyDraft)
  const [feedback, setFeedback] = useState(null)
  const [preview, setPreview] = useState(null)
  const pending = useRef(false)
  const dirty = ['name', 'subject', 'body'].some(key => draft[key] !== (saved?.[key] ?? ''))

  //--------------------------------------------------------------

  async function perform(action) {
    if (busy || pending.current) return
    pending.current = true
    setBusy(true)
    setFeedback(null)
    try { await action() } catch (error) { setFeedback({ error: true, text: error.message }) }
    finally { pending.current = false; setBusy(false) }
  }

  //--------------------------------------------------------------

  function openTemplate(id) {
    if (dirty && !window.confirm('Kassér de ændringer, der ikke er gemt?')) return
    perform(async () => {
      const template = id ? await getTemplate(id) : null
      setSaved(template)
      setDraft(template ? { name: template.name, subject: template.subject, body: template.body } : emptyDraft)
      setPreview(null)
    })
  }

  //--------------------------------------------------------------

  function edit(field, value) {
    setDraft(current => ({ ...current, [field]: value }))
    setFeedback(null)
    setPreview(null)
  }

  //--------------------------------------------------------------

  function save(event) {
    event.preventDefault()
    perform(async () => {
      const template = await saveTemplate(saved?.id, draft)
      setSaved(template)
      setDraft({ name: template.name, subject: template.subject, body: template.body })
      setPreview(null)
      onSave(template)
      setFeedback({ text: 'Skabelonen er gemt.' })
    })
  }

  //--------------------------------------------------------------

  function validate() {
    perform(async () => {
      const result = await validateTemplate(draft)
      setFeedback({ error: !result.valid, text: result.valid ? 'Alle pladsholdere er gyldige.'
        : `Ukendte pladsholdere: ${result.unknownVariables.join(', ')}` })
    })
  }

  //--------------------------------------------------------------

  function remove() {
    if (!saved || !window.confirm(`Slet skabelonen ”${saved.name}” permanent?`)) return
    perform(async () => {
      await deleteTemplate(saved.id)
      onDelete(saved.id)
      setSaved(null)
      setDraft(emptyDraft)
      setPreview(null)
      setFeedback({ text: 'Skabelonen er slettet.' })
    })
  }

  return <section className={styles.panel} aria-labelledby="templates-heading">
    <h2 id="templates-heading">Skabeloner</h2>
    <p>Opret og rediger mails som almindelig tekst. Tilgængelige pladsholdere:</p>
    <p className={styles.placeholders}>{['<Firstname>', '<Lastname>', '<Company>', '<Email>'].map(value => <code key={value}>{value}</code>)}</p>
    <label className={styles.field}>Vælg skabelon til redigering
      <select value={saved?.id || ''} onChange={event => openTemplate(event.target.value)} disabled={busy}>
        <option value="">Ny skabelon</option>
        {templates.map(template => <option key={template.id} value={template.id}>{template.name}</option>)}
      </select>
    </label>
    <form onSubmit={save}>
      <fieldset className={styles.form} disabled={busy}>
        <label className={styles.field}>Skabelonnavn<input value={draft.name} onChange={event => edit('name', event.target.value)} required maxLength={150} /></label>
        <label className={styles.field}>Emne<input value={draft.subject} onChange={event => edit('subject', event.target.value)} required maxLength={200} /></label>
        <label className={styles.field}>Brødtekst<textarea value={draft.body} onChange={event => edit('body', event.target.value)} required maxLength={5000} rows={10} /></label>
        <div className={styles.actions}>
          <button className={styles.send} type="submit">Gem skabelon</button>
          <button className={styles.secondary} type="button" onClick={validate}>Valider pladsholdere</button>
          <button className={styles.secondary} type="button" disabled={!saved || dirty}
            onClick={() => perform(async () => { setPreview(null); setPreview(await renderTemplate(saved.id, previewRecipient)) })}>Forhåndsvis</button>
          {saved && <button className={styles.secondary} type="button" onClick={remove}>Slet skabelon</button>}
        </div>
      </fieldset>
    </form>
    {dirty && <p className={styles.hint}>Du har ændringer, der ikke er gemt. Gem skabelonen før forhåndsvisning eller udsendelse.</p>}
    {feedback && <p role={feedback.error ? 'alert' : 'status'} className={feedback.error ? styles.error : styles.hint}>{feedback.text}</p>}
    {preview && <div className={styles.preview}>
      <h3>Forhåndsvisning</h3>
      <p className={styles.hint}>Eksempel: Ola Nordmann · Nord Stand ApS · ola@example.com</p>
      <p><strong>{preview.subject}</strong></p><pre>{preview.body}</pre>
      {preview.unknownVariables.length > 0 && <p role="alert" className={styles.error}>Ukendte pladsholdere: {preview.unknownVariables.join(', ')}</p>}
    </div>}
  </section>
}
