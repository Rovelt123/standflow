import { useEffect, useMemo, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import { portalRequest } from '../portal/portalApi.js'
import shell from './KontrolpanelPage.module.css'
import styles from './StadeholderePage.module.css'

function StadeholderePage() {
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')

    portalRequest('/applications')
      .then((data) => { if (active) setApplications(Array.isArray(data) ? data : []) })
      .catch((caught) => { if (active) setError(caught.message) })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [])

  const vendors = useMemo(
    () => applications
      .filter((a) => a.status === 'ACCEPTED')
      .sort((a, b) => (a.company || '').localeCompare(b.company || '', 'da')),
    [applications],
  )

  return (
    <div className={shell.page}>
      <AdminTopBar title="Stadeholdere" />
      <AdminSidebar />
      <main className={shell.main}>
        <h1 className={styles.heading}>Godkendte stadeholdere</h1>
        <p className={styles.intro}>
          Oversigt over alle stadeholdere der er godkendt til julemarkedet.
          {vendors.length > 0 ? ` I alt ${vendors.length}.` : ''}
        </p>

        {loading && <p role="status">Henter stadeholdere …</p>}
        {error && <p role="alert">{error}</p>}
        {!loading && !error && vendors.length === 0 && (
          <p>Ingen godkendte stadeholdere endnu. Godkend ansøgninger i Kontrolpanelet.</p>
        )}
        {!loading && !error && vendors.length > 0 && (
          <div className={styles.card}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Virksomhed</th>
                  <th>Kontaktperson</th>
                  <th>Email</th>
                  <th>Telefon</th>
                  <th>Standtype</th>
                  <th>Borde</th>
                  <th>Stole</th>
                </tr>
              </thead>
              <tbody>
                {vendors.map((vendor) => (
                  <tr key={vendor.id}>
                    <td>{vendor.company}</td>
                    <td>{vendor.contact}</td>
                    <td>{vendor.email || '—'}</td>
                    <td>{vendor.phone || '—'}</td>
                    <td>{vendor.standType ? `Type ${vendor.standType}` : '—'}</td>
                    <td>{vendor.tables ?? '—'}</td>
                    <td>{vendor.chairs ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}

export default StadeholderePage
