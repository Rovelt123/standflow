import { useEffect, useMemo, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import StatTile from '../../components/admin/StatTile.jsx'
import ApplicationsToolbar from '../../components/admin/ApplicationsToolbar.jsx'
import ApplicationsTable from '../../components/admin/ApplicationsTable.jsx'
import ApplicationDetail from '../../components/admin/ApplicationDetail.jsx'
import { portalRequest } from '../portal/portalApi.js'
import styles from './KontrolpanelPage.module.css'

function countBy(applications, status) {
  return applications.filter(application => application.status === status).length
}

const CSV_HEADERS = [
  'Virksomhed', 'Kontaktperson', 'CVR', 'Email', 'Telefon', 'Adresse', 'By', 'Website',
  'Produkter', 'Tidligere stadeholder', 'Standtype', 'Borde', 'Stole', 'Status', 'Modtaget', 'Intern kommentar',
]

function csvCell(value) {
  const text = value === null || value === undefined ? '' : String(value)
  return /[";\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function buildCsv(rows) {
  const lines = [CSV_HEADERS.join(';')]
  for (const a of rows) {
    lines.push([
      a.company, a.contact, a.cvr, a.email, a.phone, a.address, a.city, a.website, a.products,
      a.previousExhibitor == null ? '' : a.previousExhibitor ? 'Ja' : 'Nej',
      a.standType, a.tables, a.chairs, a.status, a.createdAt, a.comment,
    ].map(csvCell).join(';'))
  }
  return lines.join('\n')
}

function KontrolpanelPage() {
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [selected, setSelected] = useState(null)
  const [query, setQuery] = useState('')
  const [standTypeFilter, setStandTypeFilter] = useState('ALL')
  const [sortBy, setSortBy] = useState('date-desc')

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

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    const filtered = applications.filter((a) => {
      if (standTypeFilter !== 'ALL' && a.standType !== standTypeFilter) return false
      if (!q) return true
      return [a.company, a.contact, a.email, a.cvr, a.city].some(
        (field) => (field ? String(field).toLowerCase().includes(q) : false),
      )
    })
    const sorted = [...filtered]
    if (sortBy === 'company-asc') {
      sorted.sort((a, b) => (a.company || '').localeCompare(b.company || '', 'da'))
    } else {
      sorted.sort((a, b) => {
        const da = new Date(a.createdAt).getTime() || 0
        const db = new Date(b.createdAt).getTime() || 0
        return sortBy === 'date-asc' ? da - db : db - da
      })
    }
    return sorted
  }, [applications, query, standTypeFilter, sortBy])

  function applyUpdated(updated) {
    setApplications(current => current.map(item => (item.id === updated.id ? updated : item)))
    setSelected(current => (current && current.id === updated.id ? updated : current))
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

  function exportCsv() {
    const csv = buildCsv(visible)
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `ansoegninger-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
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
        <ApplicationsToolbar
          query={query}
          onQueryChange={setQuery}
          standType={standTypeFilter}
          onStandTypeChange={setStandTypeFilter}
          sortBy={sortBy}
          onSortChange={setSortBy}
          onExportCsv={exportCsv}
          resultCount={visible.length}
          canExport={visible.length > 0}
        />
        {loading && <p role="status">Henter ansøgninger …</p>}
        {error && <p role="alert">{error}</p>}
        {actionError && <p role="alert">{actionError}</p>}
        {!loading && !error && applications.length === 0 && <p>Ingen ansøgninger endnu.</p>}
        {!loading && !error && applications.length > 0 && visible.length === 0 && (
          <p>Ingen ansøgninger matcher søgningen eller filteret.</p>
        )}
        {!loading && !error && visible.length > 0 && (
          <ApplicationsTable
            applications={visible}
            onStatusChange={onStatusChange}
            onCommentSave={onCommentSave}
            onSelect={setSelected}
            busyId={busyId}
          />
        )}
      </main>
      <ApplicationDetail application={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

export default KontrolpanelPage
