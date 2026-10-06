import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { getCurrentUser } from '../../pages/public/authApi.js'

export default function RequireAdmin({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    getCurrentUser()
      .then(data => {
        if (active) setUser(data)
      })
      .catch(failure => {
        if (active) setError(failure.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [])

  if (loading) return <p role="status">Kontrollerer adgang…</p>

  if (error) {
    return (
      <div role="alert">
        <p>{error}</p>
        <a href="/login">Gå til login</a>
      </div>
    )
  }

  if (!user?.roles?.includes('ADMIN')) {
    return <Navigate to="/brugerportal" replace />
  }

  return children
}