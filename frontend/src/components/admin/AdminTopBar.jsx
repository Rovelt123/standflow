import Logo from '../ui/Logo.jsx'
import styles from './AdminTopBar.module.css'

function AdminTopBar({ title }) {
  return (
    <header className={styles.topBar}>
      <Logo />
      <span className={styles.divider} />
      <h1 className={styles.title}>{title}</h1>
    </header>
  )
}

export default AdminTopBar
