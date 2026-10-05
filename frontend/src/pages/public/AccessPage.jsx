import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import PublicHeader from '../../components/layout/PublicHeader.jsx'
import styles from './PublicPages.module.css'

export default function AccessPage({ register = false, onContinue }) {
  const navigate = useNavigate()
  const [error, setError] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    if (register && fields.get('password') !== fields.get('confirmPassword')) {
      setError('Adgangskoderne skal være ens.')
      return
    }
    onContinue()
    navigate('/ansoegning', { replace: true })
  }

  return <><PublicHeader /><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Din vej til en stand</p>
    <h1>{register ? 'Registrer dig som stadeholder' : 'Log ind for at ansøge'}</h1>
    <p className={styles.intro}>Log ind eller registrer dig for at fortsætte til ansøgningsformularen.</p>
    <nav className={styles.tabs} aria-label="Login og registrering">
      <Link to="/login" aria-current={!register ? 'page' : undefined}>Log ind</Link>
      <Link to="/registrer" aria-current={register ? 'page' : undefined}>Registrer dig</Link>
    </nav>
    <form className={`${styles.panel} ${styles.form}`} onSubmit={handleSubmit}>
      <p className={styles.notice}>Demo: Der oprettes ingen konto, og login kontrolleres ikke endnu. Brug eksempeloplysninger. Adgangskoder gemmes eller sendes ikke.</p>
      {register && <div className={styles.fields}>
        <label>Virksomhedsnavn<input name="company" autoComplete="organization" required maxLength={200} /></label>
        <label>CVR<input name="cvr" inputMode="numeric" pattern="[0-9]{8}" title="Præcis 8 cifre" required /></label>
        <label>Fornavn<input name="firstName" autoComplete="given-name" required /></label>
        <label>Efternavn<input name="lastName" autoComplete="family-name" required /></label>
        <label>Telefon<input name="phone" type="tel" autoComplete="tel" required /></label>
        <label>Adresse<input name="address" autoComplete="street-address" required /></label>
      </div>}
      <label>Email<input name="email" type="email" autoComplete="email" required /></label>
      <label>Adgangskode<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={8} required /></label>
      {register && <label>Gentag adgangskode<input name="confirmPassword" type="password" autoComplete="new-password" minLength={8} required /></label>}
      {error && <p role="alert">{error}</p>}
      <button type="submit">{register ? 'Afprøv registrering og fortsæt' : 'Afprøv login og fortsæt'}</button>
    </form>
  </div></main></>
}
