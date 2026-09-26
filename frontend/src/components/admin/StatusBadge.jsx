import styles from './StatusBadge.module.css'

const statusLabels = {
  pending: 'Under behandling',
  approved: 'Godkendt',
  rejected: 'Afvist',
}

function StatusBadge({ status }) {
  return <span className={`${styles.badge} ${styles[status]}`}>{statusLabels[status]}</span>
}

export default StatusBadge
