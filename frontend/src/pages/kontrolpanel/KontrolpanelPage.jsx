import { useEffect, useMemo, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import StatTile from '../../components/admin/StatTile.jsx'
import ApplicationsToolbar from '../../components/admin/ApplicationsToolbar.jsx'
import ApplicationsTable from '../../components/admin/ApplicationsTable.jsx'
import { portalRequest } from '../portal/portalApi.js'
import styles from './KontrolpanelPage.module.css'

function countBy(applications, status) {
  return applications.filter(application => application.status === status).length
}

function KontrolpanelPage() {
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')

    portalRequest('/applications')
      .then(data => { if (active) setApplications(Array.isArray(data) ? data : []) })
      .catch(caught => { if (active) setError(caught.message) })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [])

  const stats = useMemo(() => [
    { label: 'Indkomne ansøgninger', value: String(applications.length), note: 'I alt', tone: 'default' },
    { label: 'Under behandling', value: String(countBy(applications, 'PENDING')), note: 'Afventer svar', tone: 'default' },
    { label: 'Godkendte stadeholdere', value: String(countBy(applications, 'ACCEPTED')), note: 'Accepterede', tone: 'positive' },
    { label: 'Info efterspurgt', value: String(countBy(applications, 'INFO_REQUESTED')), note: 'Mangler oplysninger', tone: 'negative' },
  ], [applications])

  function applyUpdated(updated) {
    setApplications(current => current.map(item => (item.id === updated.id ? updated : item)))
  }

  async function onStatusChange(id, status) {
    setBusyId(id)
    setActionError('')
    try {
      applyUpdated(await portalRequest(`/applications/${id}/status`, 'PATCH', { status }))
    } catch (caught) {
      setActionError(caught.message)
    } finally {
      setBusyId(null)
    }
  }

  async function onCommentSave(id, comment) {
    setBusyId(id)
    setActionError('')
    try {
      applyUpdated(await portalRequest(`/applications/${id}/comment`, 'PUT', { comment }))
    } catch (caught) {
      setActionError(caught.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className={styles.page}>
      <AdminTopBar title="Kontrolpanel" />
      <AdminSidebar />
      <main className={styles.main}>
        <div className={styles.stats}>
          {stats.map((stat) => (
            <StatTile key={stat.label} {...stat} />
          ))}
        </div>
        <ApplicationsToolbar />
        {loading && <p role="status">Henter ansøgninger …</p>}
        {error && <p role="alert">{error}</p>}
        {actionError && <p role="alert">{actionError}</p>}
        {!loading && !error && applications.length === 0 && <p>Ingen ansøgninger endnu.</p>}
        {!loading && !error && applications.length > 0 && (
          <ApplicationsTable
            applications={applications}
            onStatusChange={onStatusChange}
            onCommentSave={onCommentSave}
            busyId={busyId}
          />
        )}
      </main>
    </div>
  )
}

export default KontrolpanelPage
