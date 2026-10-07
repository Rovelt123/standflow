import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { unsubscribeFromNewsletter } from './newsletterApi.js'
import styles from './PublicPages.module.css'

export default function UnsubscribePage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setStatus('loading')
    setError('')
    unsubscribeFromNewsletter(token)
      .then(() => { if (active) setStatus('done') })
      .catch(caught => { if (active) { setError(caught.message); setStatus('error') } })
    return () => { active = false }
  }, [token])

  return <><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Nyhedsbrev</p><h1>Afmeld nyhedsbreve</h1>
    <p className={styles.intro}>Vi behandler din afmelding fra markedsføringsmails.</p>
    <div className={styles.panel}>
      {status === 'loading' && <p role="status">Afmelder dig …</p>}
      {status === 'done' && <p className={styles.notice}>Du er nu afmeldt vores nyhedsbreve og markedsføringsmails. Du modtager stadig vigtige beskeder om din egen ansøgning.</p>}
      {status === 'error' && <p role="alert">{error}</p>}
    </div>
  </div></main></>
}
