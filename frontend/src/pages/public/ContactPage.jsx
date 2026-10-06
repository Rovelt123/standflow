import { useState } from 'react'
import styles from './PublicPages.module.css'

export default function ContactPage() {
  const [previewed, setPreviewed] = useState(false)
  return <><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Vi hører gerne fra dig</p><h1>Kontakt</h1>
    <p className={styles.intro}>Har du spørgsmål til julemarkedet eller en stand på Engestofte Gods?</p>
    <form className={`${styles.panel} ${styles.form}`} onSubmit={event => { event.preventDefault(); setPreviewed(true) }}>
      <p className={styles.notice}>Kontaktformularen er en forhåndsvisning. Beskeder kan endnu ikke sendes.</p>
      <div className={styles.fields}>
        <label>Fornavn<input name="firstName" autoComplete="given-name" required /></label>
        <label>Efternavn<input name="lastName" autoComplete="family-name" required /></label>
        <label>Email<input name="email" type="email" autoComplete="email" required /></label>
        <label>Telefon<input name="phone" type="tel" autoComplete="tel" required /></label>
      </div>
      <label>Besked<textarea name="message" rows={6} required /></label>
      <button type="submit">Afprøv formular</button>
      {previewed && <p role="status">Felterne er udfyldt. Din besked er ikke sendt, da kontaktformularen endnu er en demo.</p>}
    </form>
  </div></main></>
}
