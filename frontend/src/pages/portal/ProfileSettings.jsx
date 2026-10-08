import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteAccount, getMarketingConsent, saveProfile, unsubscribeMarketing, updateMarketingConsent } from './portalApi.js'
import styles from './PortalPage.module.css'

const fields = [
  ['company', 'Virksomhedsnavn', 200], ['firstname', 'Fornavn', 75], ['lastname', 'Efternavn', 74],
  ['cvr', 'CVR-nummer', 8], ['email', 'E-mailadresse', 254], ['phone', 'Telefonnummer', 30],
  ['address', 'Adresse', 255], ['city', 'Postnr. og by', 150],
]

export default function ProfileSettings({ user, onUpdate }) {
  const navigate = useNavigate()
  const [profile, setProfile] = useState(user)
  const [busy, setBusy] = useState(false)
  const [feedback, setFeedback] = useState(null)
  const [marketingConsent, setMarketingConsent] = useState(null)
  const [consentLoading, setConsentLoading] = useState(true)
  const [consentFeedback, setConsentFeedback] = useState(null)
  const [consentReload, setConsentReload] = useState(0)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const consentPending = useRef(false)

  useEffect(() => {
    let active = true
    getMarketingConsent().then(consent => {
      if (active) setMarketingConsent(consent)
    }).catch(error => {
      if (active) setConsentFeedback({ error: true, text: error.message })
    }).finally(() => { if (active) setConsentLoading(false) })
    return () => { active = false }
  }, [consentReload])

  //--------------------------------------------------------------

  async function changeConsent(nextConsent, unsubscribe = false) {
    if (busy || consentPending.current || consentLoading || marketingConsent === null) return
    consentPending.current = true
    setBusy(true)
    setConsentFeedback(null)
    try {
      const consent = await (unsubscribe ? unsubscribeMarketing() : updateMarketingConsent(nextConsent))
      setMarketingConsent(consent)
      setConsentFeedback({ text: consent ? 'Du er tilmeldt marketing og nyhedsbreve.' : 'Du er afmeldt marketing og nyhedsbreve.' })
    } catch (error) {
      setConsentFeedback({ error: true, text: error.message })
    } finally {
      consentPending.current = false
      setBusy(false)
    }
  }

  //--------------------------------------------------------------

  async function submit(event, action) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const body = Object.fromEntries(new FormData(form))
    setBusy(true)
    setFeedback(null)
    try {
      if (action === 'delete') {
        await deleteAccount(body.currentPassword, body.confirmDelete === 'on')
        setProfile(null)
        onUpdate(null)
        navigate('/', { replace: true })
      } else {
        if (action === 'password' && body.newPassword !== body.confirmPassword) throw new Error('De nye adgangskoder skal være ens.')
        const updated = await saveProfile(action === 'profile'
          ? { ...body, emailNotifications: body.emailNotifications === 'on' } : body, action === 'password')
        setProfile(updated)
        onUpdate(updated)
        form.querySelectorAll('input[type="password"]').forEach(input => { input.value = '' })
        setFeedback({ text: action === 'password' ? 'Din adgangskode er ændret. Andre login-sessioner er afsluttet.' : 'Dine oplysninger er gemt.' })
      }
    } catch (error) {
      setFeedback({ error: true, text: error.message })
    } finally {
      setBusy(false)
    }
  }

  if (!profile) return null

  return <div className={styles.settings}>
    {feedback && <p role={feedback.error ? 'alert' : 'status'} className={feedback.error ? styles.error : styles.notice}>{feedback.text}</p>}
    <form className={styles.card} onSubmit={event => submit(event, 'profile')}>
      <h2>Mine oplysninger</h2>
      <p>Disse oplysninger bruges som udgangspunkt, når du ansøger. Eksisterende ansøgninger ændres ikke.</p>
      <fieldset disabled={busy} className={styles.form}>
        <div className={styles.fields}>{fields.map(([name, label, maxLength]) => <label key={name}>{label}
          <input name={name} type={name === 'email' ? 'email' : 'text'} required maxLength={maxLength}
            pattern={name === 'cvr' ? '[0-9]{8}' : undefined} value={profile[name] || ''}
            onChange={event => setProfile(current => ({ ...current, [name]: event.target.value }))} />
        </label>)}</div>
        <label className={styles.check}><input type="checkbox" name="emailNotifications" checked={profile.emailNotifications}
          onChange={event => setProfile(current => ({ ...current, emailNotifications: event.target.checked }))} />Modtag notifikationer på e-mail</label>
        <label>Bekræft med din nuværende adgangskode<input type="password" name="currentPassword" autoComplete="current-password" required /></label>
        <button type="submit">Gem oplysninger</button>
      </fieldset>
    </form>
    <section className={styles.card} aria-labelledby="marketing-heading" aria-busy={consentLoading}>
      <h2 id="marketing-heading">Marketing og nyhedsbreve</h2>
      <p>Dit samtykke gælder kun marketing og nyhedsbreve. E-mails om din konto og dine notifikationer ændres ikke.</p>
      {consentLoading ? <p role="status">Henter dit samtykke…</p> : marketingConsent !== null &&
        <p>Marketing er {marketingConsent ? 'tilmeldt' : 'afmeldt'}.</p>}
      {consentFeedback && <p role={consentFeedback.error ? 'alert' : 'status'} className={consentFeedback.error ? styles.error : styles.notice}>{consentFeedback.text}</p>}
      {!consentLoading && marketingConsent !== null && <fieldset className={styles.form} disabled={busy}>
        <label className={styles.check}><input type="checkbox" checked={marketingConsent === true}
          onChange={event => changeConsent(event.target.checked)} />Jeg vil modtage marketing og nyhedsbreve</label>
        <button type="button" className={styles.secondary} disabled={marketingConsent !== true}
          onClick={() => changeConsent(false, true)}>Afmeld marketing</button>
      </fieldset>}
      {consentFeedback?.error && <button type="button" className={styles.secondary} disabled={busy || consentLoading}
        onClick={() => { setConsentLoading(true); setConsentFeedback(null); setConsentReload(current => current + 1) }}>Genindlæs samtykke</button>}
    </section>
    <form className={styles.card} onSubmit={event => submit(event, 'password')}>
      <h2>Skift adgangskode</h2>
      <p>Brug 8–30 tegn med store og små bogstaver samt et specialtegn.</p>
      <fieldset disabled={busy} className={styles.form}>
        <label>Nuværende adgangskode<input type="password" name="currentPassword" autoComplete="current-password" required /></label>
        <label>Ny adgangskode<input type="password" name="newPassword" autoComplete="new-password" minLength={8} maxLength={30} required /></label>
        <label>Gentag ny adgangskode<input type="password" name="confirmPassword" autoComplete="new-password" minLength={8} maxLength={30} required /></label>
        <button type="submit">Skift adgangskode</button>
      </fieldset>
    </form>
    <section className={`${styles.card} ${styles.danger}`}>
      <h2>Slet min bruger</h2><p>Din profil og alle dine ansøgninger slettes permanent fra portalen.</p>
      {!confirmingDelete ? <button type="button" disabled={busy} onClick={() => { setFeedback(null); setConfirmingDelete(true) }}>Slet min bruger</button> :
        <form className={styles.confirm} aria-label="Bekræft permanent sletning" onSubmit={event => submit(event, 'delete')}>
          <fieldset disabled={busy} className={styles.form}>
            <label>Din nuværende adgangskode<input type="password" name="currentPassword" autoComplete="current-password" required /></label>
            <label className={styles.check}><input type="checkbox" name="confirmDelete" required />Ved at tjekke denne boks af, accepterer du, at vi sletter din bruger og al data vil gå tabt. Dette kan ikke fortrydes!</label>
            <button type="submit">Slet min bruger permanent</button>
            <button type="button" className={styles.secondary} onClick={() => setConfirmingDelete(false)}>Annuller</button>
          </fieldset>
        </form>}
    </section>
    {busy && <p role="status">Behandler din anmodning…</p>}
  </div>
}
