const iconPaths = {
  clipboard: 'M9 4H6v17h12V4h-3 M9 3h6v4H9z M8 13l3 3 5-6',
  medal: 'M8 14l-1 7 5-3 5 3-1-7 M12 3a6 6 0 1 0 0 12 6 6 0 0 0 0-12',
  shop: 'M4 10v11h16V10 M3 10l2-7h14l2 7 M3 10q3 4 6 0 3 4 6 0 3 4 6 0 M9 21v-7h6v7',
  instagram: 'M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M17 7h.01',
  facebook: 'M14 22V13h3l1-4h-4V6q0-2 4-2V1h-3q-5 0-5 5v3H7v4h3v9',
}

function Icon({ name }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={iconPaths[name]} />
    </svg>
  )
}

export default Icon
