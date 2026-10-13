import { useState } from 'react'
import StatusBadge from './StatusBadge.jsx'
import { hasParticipatedBefore } from '../../pages/kontrolpanel/applicationParticipation.js'
import styles from './ApplicationsTable.module.css'

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Under behandling' },
  { value: 'ACCEPTED', label: 'Godkend' },
  { value: 'REJECTED', label: 'Afvis' },
  { value: 'INFO_REQUESTED', label: 'Efterspørg info' },
]

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('da-DK')
}

//--------------------------------------------------------------

function ApplicationRow({ application, onSave, onSelect, busy }) {
  const companyName = application.company?.trim() || 'Ikke angivet'
  const [draft, setDraft] = useState({})
  const values = Object.fromEntries(['standType', 'status', 'comment', 'customerNote'].map(field =>
    [field, draft[field] ?? application[field] ?? '']))
  const dirty = Object.entries(values).some(([field, value]) => value !== (application[field] ?? ''))

  //--------------------------------------------------------------

  function change(field, value) {
    setDraft(current => ({ ...current, [field]: value }))
  }

  //--------------------------------------------------------------

  async function save() {
    if (await onSave(application, values)) setDraft({})
  }

  //--------------------------------------------------------------

  return (
    <tr>
      <td><div className={styles.companyCell}>
        <button type="button" className={styles.companyButton} disabled={busy}
          onClick={() => onSelect(application)} aria-label={`Se detaljer for ${companyName}`}>
          {companyName}
        </button>
        <StatusBadge status={application.status} />
      </div></td>
      <td>{hasParticipatedBefore(application) ? 'Ja' : 'Nej'}</td>
      <td className={styles.muted}>{application.contact}</td>
      <td>
        <select className={styles.statusSelect} value={values.standType} disabled={busy}
          aria-label={`Standtype for ${application.company}`} onChange={event => change('standType', event.target.value)}>
          {'ABCDEFGH'.split('').map(type => <option key={type} value={type}>Type {type}</option>)}
        </select>
      </td>
      <td><div className={styles.statusCell}>
        <select className={styles.statusSelect} value={values.status} disabled={busy}
          aria-label={`Skift status for ${application.company}`} onChange={event => change('status', event.target.value)}>
          {STATUS_OPTIONS.map(option => <option key={option.value} value={option.value}
            disabled={option.value === 'PENDING' && application.status !== 'PENDING'}>{option.label}</option>)}
        </select>
      </div></td>
      <td className={styles.muted}>{formatDate(application.createdAt)}</td>
      <td><div className={styles.comment}>
        <label className={styles.commentLabel}>Intern kommentar
          <input className={styles.commentInput} value={values.comment} maxLength={5000}
            placeholder="Intern note (fx mangler CVR)" aria-label={`Intern kommentar for ${application.company}`}
            disabled={busy} onChange={event => change('comment', event.target.value)} />
        </label>
        <label className={styles.commentLabel}>Note til kunden
          <input className={styles.commentInput} value={values.customerNote} maxLength={5000}
            placeholder="Fx oplys venligst dit CVR-nummer" aria-label={`Note til kunden for ${application.company}`}
            disabled={busy} onChange={event => change('customerNote', event.target.value)} />
        </label>
        <button type="button" className={styles.saveButton} disabled={busy || !dirty} onClick={save}>Gem</button>
      </div></td>
    </tr>
  )
}

//--------------------------------------------------------------
function ApplicationsTable({ applications, onSave, onSelect, busyId }) {
  return (
    <div className={styles.card}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th className={styles.companyColumn}>Virksomhed</th>
            <th className={styles.participationHeader}>Deltaget før</th>
            <th>Kontaktperson</th>
            <th>Standtype</th>
            <th>Status</th>
            <th className={styles.dateHeader}>Dato</th>
            <th>Kommentarer</th>
          </tr>
        </thead>
        <tbody>
          {applications.map(application => (
            <ApplicationRow key={application.id} application={application} onSave={onSave}
              onSelect={onSelect} busy={Boolean(busyId)} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default ApplicationsTable
