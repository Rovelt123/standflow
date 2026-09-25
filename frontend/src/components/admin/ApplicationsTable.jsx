import StatusBadge from './StatusBadge.jsx'
import styles from './ApplicationsTable.module.css'

function ApplicationsTable({ applications }) {
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
          </tr>
        </thead>
        <tbody>
          {applications.map((application) => (
            <tr key={application.id}>
              <td>{application.company}</td>
              <td className={styles.muted}>{application.contact}</td>
              <td>{application.standType}</td>
              <td>
                <StatusBadge status={application.status} />
              </td>
              <td className={styles.muted}>{application.date}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default ApplicationsTable
