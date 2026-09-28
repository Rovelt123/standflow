import styles from './StatTile.module.css'

function StatTile({ label, value, note, tone }) {
  return (
    <section className={styles.tile}>
      <h2 className={styles.label}>{label}</h2>
      <p className={`${styles.value} ${styles[tone]}`}>{value}</p>
      <p className={styles.note}>{note}</p>
    </section>
  )
}

export default StatTile
