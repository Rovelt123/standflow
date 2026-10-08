import { useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { unsubscribeFromNewsletter } from './newsletterApi.js'
import styles from './PublicPages.module.css'

export default function UnsubscribePage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  return <UnsubscribeConfirmation key={token} token={token} />
}

//--------------------------------------------------------------

function UnsubscribeConfirmation({ token }) {
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState('')
  const pending = useRef(false)

  //--------------------------------------------------------------

  async function unsubscribe(event) {
    event.preventDefault()
    if (pending.current || status === 'done' || !token.trim()) return
    pending.current = true
    setStatus('loading')
    setError('')
    try {
      await unsubscribeFromNewsletter(token)
      setStatus('done')
    } catch (failure) {
      setError(failure.message)
      setStatus('error')
    } finally {
      pending.current = false
    }
  }

  return <main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Nyhedsbrev</p><h1>Afmeld nyhedsbreve</h1>
    <p className={styles.intro}>Du kan afmelde nyheder og markedsføring fra StandFlow her.</p>
    <form className={`${styles.panel} ${styles.form}`} onSubmit={unsubscribe}>
      {!token.trim() ? <p role="alert">Afmeldingslinket er ufuldstændigt. Brug linket i dit nyhedsbrev.</p>
        : status === 'done' ? <p role="status">Du er nu afmeldt vores nyhedsbreve og markedsføringsmails. Du modtager stadig vigtige beskeder om din egen ansøgning.</p>
          : <>
            <p>Bekræft nedenfor, at du vil afmeldes nyhedsbrevet.</p>
            <button type="submit" disabled={status === 'loading'}>{status === 'loading' ? 'Afmelder…' : 'Ja, afmeld mig'}</button>
          </>}
      {error && <p role="alert">{error}</p>}
      <Link to="/brugerportal?tab=settings">Gå til dine indstillinger</Link>
    </form>
  </div></main>
}
