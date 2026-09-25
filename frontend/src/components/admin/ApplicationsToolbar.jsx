import styles from './ApplicationsToolbar.module.css'

function ApplicationsToolbar({ search, onSearchChange, standType, onStandTypeChange, standTypes, onExport }) {
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
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </label>
        <span className={styles.separator} />
        <label className={styles.selectWrapper}>
          <span className={styles.selectLabel}>Standtype:</span>
          <select
            className={styles.select}
            aria-label="Standtype"
            value={standType}
            onChange={(event) => onStandTypeChange(event.target.value)}
          >
            <option value="">Alle</option>
            {standTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <svg className={styles.chevron} viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 9l6 6 6-6" />
          </svg>
        </label>
      </div>
      <button type="button" className={styles.exportButton} onClick={onExport}>
        Eksportér CSV
      </button>
    </div>
  )
}

export default ApplicationsToolbar
