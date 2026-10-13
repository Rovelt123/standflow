import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { verifyEmail, resendVerification } from './authApi.js'
import styles from './PublicPages.module.css'

export default function EmailVerificationPage() {
  const location = useLocation()
  const token = new URLSearchParams(location.hash.slice(1)).get('token') || ''
  const registeredEmail = location.state?.registered ? location.state.email : ''
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [verified, setVerified] = useState(false)

  //--------------------------------------------------------------

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const email = registeredEmail || new FormData(event.currentTarget).get('email')
    setError('')
    setMessage('')
    setSubmitting(true)
    try {
      if (!token) {
        await resendVerification(email.trim())
        setMessage('Hvis kontoen afventer bekræftelse, modtager du en ny mail. Tjek også spam. Vent mindst et minut, før du prøver igen.')
      } else {
        await verifyEmail(token)
        setVerified(true)
      }
    } catch (failure) {
      setError(failure.message)
    } finally {
      setSubmitting(false)
    }
  }

  return <main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Din konto</p>
    <h1>Bekræft din e-mailadresse</h1>
    {verified ? <p role="status">Din e-mailadresse er bekræftet. Du kan nu logge ind. Vi sender dig også en velkomstmail.</p> : <>
      <p className={styles.intro}>{location.state?.registered
        ? 'Din konto er oprettet. Tjek din mail og følg linket for at bekræfte din e-mailadresse, før du logger ind.'
        : 'Bekræft din e-mailadresse via linket i din mail. Linket gælder i 24 timer.'}</p>
      {token && <form className={`${styles.panel} ${styles.form}`} onSubmit={handleSubmit}>
        <button type="submit" disabled={submitting}>Bekræft e-mailadresse</button>
      </form>}
      {!token && <form className={`${styles.panel} ${styles.form}`} onSubmit={handleSubmit}>
        <p>Har du ikke modtaget mailen, eller er linket udløbet? Bed om et nyt link.</p>
        {!registeredEmail && <label>Email<input name="email" type="email" autoComplete="email" maxLength={254} required /></label>}
        <button type="submit" disabled={submitting}>{submitting ? 'Vent venligst…' : 'Send bekræftelsesmail igen'}</button>
      </form>}
    </>}
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {(!registeredEmail || token || verified) && <Link className={styles.link} to="/login">Tilbage til login</Link>}
  </div></main>
}
