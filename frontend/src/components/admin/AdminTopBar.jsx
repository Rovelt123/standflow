import styles from './AdminTopBar.module.css'

function AdminTopBar({ title }) {
  return (
    <header className={styles.topBar}>
      <div className={styles.logo}>
        <span>Engestofte</span>
        <span className={styles.logoSub}>Gods</span>
      </div>
      <span className={styles.divider} />
      <h1 className={styles.title}>{title}</h1>
    </header>
  )
}

export default AdminTopBar
