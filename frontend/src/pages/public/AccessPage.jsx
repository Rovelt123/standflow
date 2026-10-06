import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import styles from './PublicPages.module.css'
import { authenticate, getCurrentUser } from './authApi.js'

export default function AccessPage({ register = false }) {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  //--------------------------------------------------------------

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    setError('')
    const fields = new FormData(event.currentTarget)
    if (register && fields.get('password') !== fields.get('confirmPassword')) {
      setError('Adgangskoderne skal være ens.')
      return
    }
    const request = Object.fromEntries(fields)
    if (register) {
      request.acceptTerms = fields.has('acceptTerms')
      request.acceptPrivacy = fields.has('acceptPrivacy')
      request.acceptMarketing = fields.has('acceptMarketing')
    } else {
      request.rememberMe = fields.has('rememberMe')
    }
    setSubmitting(true)
    try {
      await authenticate(request, register)
      const user = await getCurrentUser()

      navigate(
        user.roles?.includes('ADMIN') ? '/kontrolpanel' : '/brugerportal',
        { replace: true }
      )
    } catch (failure) {
      setError(failure.message)
    } finally {
      setSubmitting(false)
    }
  }

  return <><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Din vej til en stand</p>
    <h1>{register ? 'Registrer dig som stadeholder' : 'Log ind for at ansøge'}</h1>
    <p className={styles.intro}>Log ind eller registrer dig for at fortsætte til ansøgningsformularen.</p>
    <nav className={styles.tabs} aria-label="Login og registrering">
      <Link to="/login" aria-current={!register ? 'page' : undefined}>Log ind</Link>
      <Link to="/registrer" aria-current={register ? 'page' : undefined}>Registrer dig</Link>
    </nav>
    <form className={`${styles.panel} ${styles.form}`} onSubmit={handleSubmit}>
      {register && <div className={styles.fields}>
        <label>Virksomhedsnavn<input name="company" autoComplete="organization" required maxLength={200} /></label>
        <label>CVR<input name="cvr" inputMode="numeric" pattern="[0-9]{8}" title="Præcis 8 cifre" required /></label>
        <label>Fornavn<input name="firstName" autoComplete="given-name" required /></label>
        <label>Efternavn<input name="lastName" autoComplete="family-name" required /></label>
        <label>Telefon<input name="phone" type="tel" autoComplete="tel" required /></label>
        <label>Adresse<input name="address" autoComplete="street-address" required /></label>
        <label>Postnr. og by<input name="city" autoComplete="address-level2" maxLength={150} required /></label>
      </div>}
      <label>Email<input name="email" type="email" autoComplete="email" required /></label>
      <label>Adgangskode<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={8} required /></label>
      {register && <label>Gentag adgangskode<input name="confirmPassword" type="password" autoComplete="new-password" minLength={8} required /></label>}
      {error && <p role="alert">{error}</p>}
      {register && <>
        <p className={styles.notice}>Adgangskoden skal være 8–30 tegn og indeholde store og små bogstaver samt et specialtegn.</p>
        <label className={styles.checkbox}><input name="acceptTerms" type="checkbox" required />Jeg accepterer betingelserne (påkrævet).</label>
        <label className={styles.checkbox}><input name="acceptPrivacy" type="checkbox" required />Jeg accepterer privatlivspolitikken (påkrævet).</label>
        <label className={styles.checkbox}><input name="acceptMarketing" type="checkbox" />Jeg ønsker markedsføring og nyhedsbreve (valgfrit).</label>
      </>}
      {!register && <label className={styles.checkbox}><input name="rememberMe" type="checkbox" />Husk mig</label>}
      <button type="submit" disabled={submitting}>{submitting ? 'Vent venligst…' : register ? 'Registrer og fortsæt' : 'Log ind og fortsæt'}</button>
    </form>
  </div></main></>
}
