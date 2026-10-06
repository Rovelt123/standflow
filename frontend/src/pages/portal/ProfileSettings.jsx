import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clearAuth } from '../public/authApi.js'
import { portalRequest, saveProfile } from './portalApi.js'
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
        await portalRequest('/users/me', 'DELETE', { currentPassword: body.currentPassword, confirmDelete: body.confirmDelete === 'on' })
        clearAuth()
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
    <form className={`${styles.card} ${styles.danger}`} onSubmit={event => submit(event, 'delete')}>
      <h2>Slet min bruger</h2><p>Din profil og alle dine ansøgninger slettes permanent fra portalen.</p>
      <fieldset disabled={busy} className={styles.form}>
        <label>Din nuværende adgangskode<input type="password" name="currentPassword" autoComplete="current-password" required /></label>
        <label className={styles.check}><input type="checkbox" name="confirmDelete" required />Ved at tjekke denne boks af, accepterer du, at vi sletter din bruger og al data vil gå tabt. Dette kan ikke fortrydes!</label>
        <button type="submit">Slet min bruger permanent</button>
      </fieldset>
    </form>
  </div>
}
