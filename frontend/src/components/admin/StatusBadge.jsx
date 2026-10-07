import styles from './StatusBadge.module.css'

// Matches app.enums.ApplicationStatus (name()).
const statusLabels = {
  PENDING: 'Under behandling',
  ACCEPTED: 'Godkendt',
  REJECTED: 'Afvist',
  INFO_REQUESTED: 'Info efterspurgt',
}

const statusClass = {
  PENDING: styles.pending,
  ACCEPTED: styles.approved,
  REJECTED: styles.rejected,
  INFO_REQUESTED: styles.info,
}

function StatusBadge({ status }) {
  return <span className={`${styles.badge} ${statusClass[status] || styles.pending}`}>{statusLabels[status] || status}</span>
}

export default StatusBadge
