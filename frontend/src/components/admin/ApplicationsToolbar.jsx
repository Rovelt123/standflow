import styles from './ApplicationsToolbar.module.css'

const STAND_TYPES = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

const SORT_OPTIONS = [
  { value: 'date-desc', label: 'Nyeste først' },
  { value: 'date-asc', label: 'Ældste først' },
  { value: 'company-asc', label: 'Virksomhed A–Å' },
]

function ApplicationsToolbar({
  query,
  onQueryChange,
  standType,
  onStandTypeChange,
  sortBy,
  onSortChange,
  onExportCsv,
  resultCount,
  canExport,
}) {
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
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
          />
        </label>
        <span className={styles.separator} />
        <select
          className={styles.select}
          value={standType}
          onChange={(event) => onStandTypeChange(event.target.value)}
          aria-label="Filtrér efter standtype"
        >
          <option value="ALL">Standtype: Alle</option>
          {STAND_TYPES.map((type) => (
            <option key={type} value={type}>Standtype: {type}</option>
          ))}
        </select>
        <span className={styles.separator} />
        <select
          className={styles.select}
          value={sortBy}
          onChange={(event) => onSortChange(event.target.value)}
          aria-label="Sortér ansøgninger"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </div>
      <button
        type="button"
        className={styles.exportButton}
        onClick={onExportCsv}
        disabled={!canExport}
      >
        Eksportér CSV{typeof resultCount === 'number' ? ` (${resultCount})` : ''}
      </button>
    </div>
  )
}

export default ApplicationsToolbar
