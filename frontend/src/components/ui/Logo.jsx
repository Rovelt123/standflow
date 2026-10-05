import styles from './Logo.module.css'

function Logo() {
  return (
    <span aria-label="Engestofte Gods" className={styles.logo}>
      <span>Engestofte</span><span>Gods</span>
    </span>
  )
}

export default Logo
