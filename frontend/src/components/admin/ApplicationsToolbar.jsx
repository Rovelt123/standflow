import styles from './ApplicationsToolbar.module.css'

function ApplicationsToolbar() {
  return (
    <div className={styles.toolbar}>
      <div className={styles.filters}>
        <label className={styles.search}>
          <svg className={styles.searchIcon} viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="M15.5 15.5L20 20" />
          </svg>
          <input
            type="search"
            className={styles.searchInput}
            placeholder="Søg i ansøgninger..."
            aria-label="Søg i ansøgninger"
            readOnly
          />
        </label>
        <span className={styles.separator} />
        <button type="button" aria-disabled="true" className={styles.selectWrapper}>
          <span className={styles.selectLabel}>Standtype: Alle</span>
          <svg className={styles.chevron} viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 9l6 6 6-6" />
          </svg>
        </button>
      </div>
      <button type="button" aria-disabled="true" className={styles.exportButton}>
        Eksportér CSV
      </button>
    </div>
  )
}

export default ApplicationsToolbar
