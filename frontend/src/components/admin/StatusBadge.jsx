import { statusLabels } from './statusLabels.js'
import styles from './StatusBadge.module.css'

function StatusBadge({ status }) {
  return <span className={`${styles.badge} ${styles[status]}`}>{statusLabels[status]}</span>
}

export default StatusBadge
