import { useEffect, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import { getTemplates } from './newsletterAdminApi.js'
import TemplateManager from './TemplateManager.jsx'
import NewsletterDelivery from './NewsletterDelivery.jsx'
import shell from './KontrolpanelPage.module.css'
import styles from './NewsletterPage.module.css'

export default function NewsletterPage() {
  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    getTemplates().then(data => { if (active) setTemplates(data) })
      .catch(failure => { if (active) setError(failure.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reload])

  //--------------------------------------------------------------

  function saved(template) {
    setTemplates(current => current.some(item => item.id === template.id)
      ? current.map(item => item.id === template.id ? template : item) : [...current, template])
  }

  return <div className={`${shell.page} ${styles.page}`}>
    <AdminTopBar title="Nyhedsbrev" /><AdminSidebar />
    <main className={`${shell.main} ${styles.main}`}>
      <h1>Nyhedsbreve</h1>
      <p className={styles.intro}>Administrer skabeloner, og send nyheder til dine stadeholdere.</p>
      {loading && <p role="status">Henter skabeloner…</p>}
      {error && <div className={styles.panel}><p className={styles.error} role="alert">{error}</p>
        <button type="button" className={styles.secondary} onClick={() => { setLoading(true); setError(''); setReload(value => value + 1) }}>Hent skabeloner igen</button></div>}
      {!loading && !error && <>
        {templates.length === 0 && <p>Ingen skabeloner endnu. Opret din første skabelon nedenfor.</p>}
        {busy && <p role="status">Behandler din anmodning…</p>}
        <div className={styles.columns}>
          <TemplateManager templates={templates} busy={busy} setBusy={setBusy} onSave={saved}
            onDelete={id => setTemplates(current => current.filter(template => template.id !== id))} />
          <NewsletterDelivery templates={templates} busy={busy} setBusy={setBusy} />
        </div>
      </>}
    </main>
  </div>
}
