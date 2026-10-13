import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { requestPasswordReset, resetPassword } from './authApi.js'
import styles from './PublicPages.module.css'

export default function PasswordResetPage({ reset = false }) {
  const location = useLocation()
  const token = new URLSearchParams(location.hash.slice(1)).get('token') || ''
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  //--------------------------------------------------------------

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    setError('')
    const fields = Object.fromEntries(new FormData(event.currentTarget))
    if (reset && fields.newPassword !== fields.confirmPassword) {
      setError('Adgangskoderne skal være ens.')
      return
    }
    setSubmitting(true)
    try {
      if (reset) await resetPassword({ ...fields, token })
      else await requestPasswordReset(fields.email.trim())
      setSuccess(true)
    } catch (failure) {
      setError(failure.message)
    } finally {
      setSubmitting(false)
    }
  }

  return <main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Din konto</p>
    <h1>{reset ? 'Vælg en ny adgangskode' : 'Glemt adgangskode?'}</h1>
    <p className={styles.intro}>{reset
      ? 'Indtast og gentag din nye adgangskode.'
      : 'Indtast din e-mailadresse, så sender vi et link til at nulstille din adgangskode. Linket gælder i 30 minutter.'}</p>
    {success ? <div className={styles.panel}><p role="status">{reset
      ? 'Din adgangskode er ændret. Du kan nu logge ind med din nye adgangskode.'
      : 'Hvis e-mailadressen er registreret, modtager du et nulstillingslink. Tjek også din spammappe. Hvis mailen ikke kommer, kan du prøve igen efter et minut.'}</p></div>
      : reset && !validToken ? <p role="alert">Nulstillingslinket mangler eller er ugyldigt. Bed om et nyt link.</p>
        : <form className={`${styles.panel} ${styles.form}`} onSubmit={handleSubmit}>
          {reset ? <>
            <p className={styles.notice}>Adgangskoden skal være 8–30 tegn og indeholde store og små bogstaver samt et specialtegn.</p>
            <label>Ny adgangskode<input name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={30} required /></label>
            <label>Gentag adgangskode<input name="confirmPassword" type="password" autoComplete="new-password" minLength={8} maxLength={30} required /></label>
          </> : <label>Email<input name="email" type="email" autoComplete="email" maxLength={254} required /></label>}
          {error && <p role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>{submitting ? 'Vent venligst…' : reset ? 'Gem ny adgangskode' : 'Send nulstillingslink'}</button>
        </form>}
    <nav className={styles.tabs} aria-label="Adgangskode og login">
      <Link to="/login">Tilbage til login</Link>
      {reset && !success && <Link to="/glemt-adgangskode">Bed om et nyt link</Link>}
    </nav>
  </div></main>
}
