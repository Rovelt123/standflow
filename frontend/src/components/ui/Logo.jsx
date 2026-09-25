import styles from './Logo.module.css'

function Logo() {
  return (
    <button type="button" aria-disabled="true" aria-label="Engestofte Gods" className={styles.logo}>
      <span>Engestofte</span><span>Gods</span>
    </button>
  )
}

export default Logo
