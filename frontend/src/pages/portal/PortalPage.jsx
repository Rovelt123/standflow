import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { clearAuth, getCurrentUser } from '../public/authApi.js'
import { portalRequest } from './portalApi.js'
import ProfileSettings from './ProfileSettings.jsx'
import styles from './PortalPage.module.css'
import CustomerChat from './CustomerChat.jsx'

const statusLabels = { PENDING: 'Afventer behandling', ACCEPTED: 'Accepteret', REJECTED: 'Afvist', INFO_REQUESTED: 'Oplysninger efterspurgt' }
const detailFields = { company: 'Virksomhed', contact: 'Kontaktperson', cvr: 'CVR', email: 'E-mail', phone: 'Telefon', address: 'Adresse', city: 'Postnr. og by', website: 'Website', products: 'Produkter', standType: 'Standtype', tables: 'Borde', chairs: 'Stole' }

export default function PortalPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = ['applications', 'messages', 'settings'].includes(params.get('tab')) ? params.get('tab') : 'applications'
  const [user, setUser] = useState(null)
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deleting, setDeleting] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([getCurrentUser(), portalRequest('/applications/mine')]).then(([profile, list]) => {
      if (active) { setUser(profile); setApplications(list) }
    }).catch(failure => { if (active) setError(failure.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  //--------------------------------------------------------------

  async function refresh() {
    setBusy(true)
    setError('')
    try {
      const [profile, list] = await Promise.all([getCurrentUser(), portalRequest('/applications/mine')])
      setUser(profile)
      setApplications(list)
    } catch (failure) { setError(failure.message) } finally { setBusy(false) }
  }

  //--------------------------------------------------------------

  async function deleteApplication(id) {
    setBusy(true)
    setError('')
    try {
      await portalRequest(`/applications/mine/${id}`, 'DELETE')
      setApplications(current => current.filter(application => application.id !== id))
      setDeleting(null)
    } catch (failure) { setError(failure.message) } finally { setBusy(false) }
  }

  return <main className={styles.page}><div className={styles.container}>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Din plads på julemarkedet</p><h1>Min brugerportal</h1>
      {user && <p>Velkommen, {user.firstname}. Her finder du dine ansøgninger og oplysninger.</p>}</div>
      <button className={styles.secondary} onClick={() => { clearAuth(); navigate('/', { replace: true }) }}>Log ud</button>
    </div>
    <nav className={styles.tabs} aria-label="Brugerportal">
      {[['applications', 'Mine ansøgninger'], ['messages', 'Beskeder'], ['settings', 'Profil & indstillinger']].map(([id, label]) =>
        <button key={id} onClick={() => setParams({ tab: id })} aria-current={tab === id ? 'page' : undefined}>{label}</button>)}
    </nav>
    {error && <p className={styles.error} role="alert">{error} <button disabled={busy} onClick={refresh}>Prøv igen</button></p>}
    {loading ? <p role="status">Henter dine oplysninger…</p> : <>
      {tab === 'applications' && <section>
        <div className={styles.heading}><h2>Mine ansøgninger</h2><div className={styles.actions}>
          <button className={styles.secondary} disabled={busy} onClick={refresh}>Opdater status</button><Link className={styles.button} to="/ansoegning">Ansøg om en stand</Link>
        </div></div>
        {!error && applications.length === 0 && <div className={styles.card}><h3>Din første ansøgning starter her</h3><p>Du har endnu ingen ansøgninger. Dine brugeroplysninger udfyldes automatisk, når du ansøger.</p></div>}
        <div className={styles.list}>{applications.map(application => <article className={styles.card} key={application.id}>
          <div className={styles.heading}><div><h3>{application.company}</h3><p>Indsendt {application.createdAt?.split('-').reverse().join('.')} · Stand {application.standType}</p></div>
            <span className={`${styles.status} ${styles[application.status] || ''}`}>{statusLabels[application.status] || application.status}</span></div>
          {application.status === 'INFO_REQUESTED' && (<div className={styles.notice}><p> Lise har efterspurgt ændringer. Ret oplysningerne og send ansøgningen til behandling igen.</p>
              {application.customerNote?.trim() && (<p className={styles.customerNote}>Note: {application.customerNote}</p>)}</div>)}
          <details><summary>Se ansøgning</summary><dl className={styles.details}>{Object.entries(detailFields).map(([field, label]) =>
            <div key={field}><dt>{label}</dt><dd>{application[field] ?? 'Ikke angivet'}</dd></div>)}<div><dt>Tidligere stadeholder</dt><dd>{application.previousExhibitor ? 'Ja' : 'Nej'}</dd></div></dl></details>
          <div className={styles.actions}>
            {application.status === 'INFO_REQUESTED' && <Link className={styles.button} to={`/brugerportal/ansoegninger/${application.id}/rediger`}>Ret ansøgning</Link>}
            <button className={styles.secondary} disabled={busy} onClick={() => setDeleting(application.id)}>Slet ansøgning</button>
          </div>
          {deleting === application.id && <div className={styles.confirm} role="group" aria-label="Bekræft sletning">
            <p>Vil du slette denne ansøgning? Det kan ikke fortrydes.</p><div className={styles.actions}>
              <button disabled={busy} onClick={() => deleteApplication(application.id)}>Ja, slet ansøgningen</button>
              <button className={styles.secondary} disabled={busy} onClick={() => setDeleting(null)}>Annuller</button>
            </div></div>}
        </article>)}</div>
      </section>}
      {tab === 'messages' && <CustomerChat user={user}/>}
      {tab === 'settings' && user && <ProfileSettings user={user} onUpdate={setUser} />}
    </>}
  </div></main>
}
