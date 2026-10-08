import { useState } from 'react'
import StatusBadge from './StatusBadge.jsx'
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

function CommentCell({ application, onSave, busy }) {
  const [comment, setComment] = useState(application.comment ?? '')
  const [customerNote, setCustomerNote] = useState(application.customerNote ?? '')
  const dirty = comment !== (application.comment ?? '') || customerNote !== (application.customerNote ?? '')
  return (
    <div className={styles.comment}>
      <label className={styles.commentLabel}>
        Intern kommentar
        <input
        className={styles.commentInput}
        value={comment}
        placeholder="Intern note (fx mangler CVR)"
        aria-label={`Intern kommentar for ${application.company}`}
        onChange={event => setComment(event.target.value)}
        disabled={busy}
        maxLength={5000}
        />
      </label>
      <label className={styles.commentLabel}>
        Note til kunden
        <input
          className={styles.commentInput}
          value={customerNote}
          placeholder="Fx oplys venligst dit CVR-nummer"
          aria-label={`Note til kunden for ${application.company}`}
          onChange={event => setCustomerNote(event.target.value)}
          disabled={busy}
          maxLength={5000}
        />
      </label>
      <button type="button" className={styles.saveButton} disabled={busy || !dirty} onClick={() => onSave(application.id, comment, customerNote)}>
        Gem
      </button>
    </div>
  )
}

//--------------------------------------------------------------

function ApplicationsTable({ applications, onStatusChange, onCommentSave, onSelect, busyId }) {
  return (
    <div className={styles.card}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th className={styles.companyColumn}>Virksomhed</th>
            <th>Kontaktperson</th>
            <th>Standtype</th>
            <th>Status</th>
            <th className={styles.dateHeader}>Dato</th>
            <th>Kommentarer</th>
          </tr>
        </thead>
        <tbody>
          {applications.map((application) => {
            const busy = busyId === application.id
            return (
              <tr key={application.id}>
                <td>
                  <button
                    type="button"
                    className={styles.companyButton}
                    onClick={() => onSelect(application)}
                    aria-label={`Se detaljer for ${application.company}`}
                  >
                    {application.company}
                  </button>
                </td>
                <td className={styles.muted}>{application.contact}</td>
                <td>{application.standType ? `Type ${application.standType}` : '—'}</td>
                <td>
                  <div className={styles.statusCell}>
                    <StatusBadge status={application.status} />
                    <select
                      className={styles.statusSelect}
                      value={application.status}
                      aria-label={`Skift status for ${application.company}`}
                      disabled={busy}
                      onChange={event => onStatusChange(application.id, event.target.value)}
                    >
                      {STATUS_OPTIONS.map(option => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </div>
                </td>
                <td className={styles.muted}>{formatDate(application.createdAt)}</td>
                <td>
                  <CommentCell application={application} onSave={onCommentSave} busy={busy} />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default ApplicationsTable
