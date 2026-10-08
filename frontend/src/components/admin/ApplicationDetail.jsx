import { useEffect } from 'react'
import StatusBadge from './StatusBadge.jsx'
import styles from './ApplicationDetail.module.css'

const FIELDS = [
  ['Virksomhed', (a) => a.company],
  ['Kontaktperson', (a) => a.contact],
  ['CVR', (a) => a.cvr],
  ['Email', (a) => a.email],
  ['Telefon', (a) => a.phone],
  ['Adresse', (a) => a.address],
  ['By', (a) => a.city],
  ['Website', (a) => a.website],
  ['Produkter', (a) => a.products],
  ['Tidligere stadeholder', (a) => (a.previousExhibitor == null ? '' : a.previousExhibitor ? 'Ja' : 'Nej')],
  ['Standtype', (a) => (a.standType ? `Type ${a.standType}` : '')],
  ['Borde', (a) => (a.tables ?? '')],
  ['Stole', (a) => (a.chairs ?? '')],
]

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('da-DK')
}

export default function ApplicationDetail({ application, onClose }) {
  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  if (!application) return null

  return (
    <div
      className={styles.overlay}
      role="dialog"
      aria-modal="true"
      aria-label={`Ansøgning fra ${application.company}`}
      onClick={onClose}
    >
      <div className={styles.panel} onClick={(event) => event.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>{application.company}</h2>
            <p className={styles.sub}>Modtaget {formatDate(application.createdAt)}</p>
          </div>
          <div className={styles.headerRight}>
            <StatusBadge status={application.status} />
            <button type="button" className={styles.close} onClick={onClose} aria-label="Luk">
              &times;
            </button>
          </div>
        </div>

        <dl className={styles.grid}>
          {FIELDS.map(([label, get]) => {
            const value = get(application)
            const display = value === '' || value === null || value === undefined ? '—' : String(value)
            return (
              <div className={styles.row} key={label}>
                <dt className={styles.label}>{label}</dt>
                <dd className={styles.value}>{display}</dd>
              </div>
            )
          })}
        </dl>

        {application.comment ? (
          <div className={styles.note}>
            <h3 className={styles.noteTitle}>Intern kommentar</h3>
            <p className={styles.noteBody}>{application.comment}</p>
          </div>
        ) : null}
      </div>
    </div>
  )
}
