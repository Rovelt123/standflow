import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { getCurrentUser } from './authApi.js'
import styles from './PublicPages.module.css'

export default function MessagesRedirect() {
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getCurrentUser().then(profile => { if (active) setUser(profile) })
      .catch(failure => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [])

  if (error) return <main className={styles.page}><p role="alert">{error}</p>
    <Link to="/login" state={{ returnTo: '/messages' }}>Log ind for at læse beskeder</Link></main>
  if (!user) return <p role="status">Åbner dine beskeder…</p>
  return <Navigate to={user.roles?.includes('ADMIN') ? '/kontrolpanel/beskeder' : '/brugerportal?tab=messages'} replace />
}
