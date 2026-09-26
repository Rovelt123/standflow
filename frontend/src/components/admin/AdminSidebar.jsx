import styles from './AdminSidebar.module.css'

const iconPaths = {
  overview: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  applications: 'M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 16h6',
  standholders: 'M4 10l1.5-5h13L20 10M4 10v9h16v-9M4 10c0 1.5 1.3 2.5 2.7 2.5S9.3 11.5 9.3 10c0 1.5 1.3 2.5 2.7 2.5s2.7-1 2.7-2.5c0 1.5 1.3 2.5 2.6 2.5S20 11.5 20 10M10 19v-4h4v4',
  sitePlan: 'M4 4h16v7H4zM4 14h7v6H4zM14 14h6v6h-6z',
  economy: 'M4 5h16v14H4zM4 9l8 5 8-5',
  settings: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2l1.8 2.6 3.1-.7.7 3.1L20.2 9l-1.3 3 1.3 3-2.6 1.9-.7 3.1-3.1-.7L12 22l-1.8-2.7-3.1.7-.7-3.1L3.8 15l1.3-3-1.3-3 2.6-2 .7-3.1 3.1.7z',
}

const navItems = [
  { label: 'Oversigt', icon: 'overview' },
  { label: 'Ansøgninger', icon: 'applications' },
  { label: 'Stadeholdere', icon: 'standholders' },
  { label: 'Plantegning', icon: 'sitePlan' },
  { label: 'Økonomi', icon: 'economy' },
  { label: 'Indstillinger', icon: 'settings' },
]

function AdminSidebar() {
  return (
    <nav className={styles.sidebar}>
      <ul className={styles.list}>
        {navItems.map((item) => (
          <li key={item.label}>
            <button type="button" aria-disabled="true" className={styles.item}>
              <svg className={styles.icon} viewBox="0 0 24 24" aria-hidden="true">
                <path d={iconPaths[item.icon]} />
              </svg>
              {item.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export default AdminSidebar
