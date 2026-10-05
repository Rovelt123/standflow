import { Link, NavLink } from 'react-router-dom'
import Button from '../ui/Button.jsx'
import Icon from '../ui/Icon.jsx'
import Logo from '../ui/Logo.jsx'
import styles from './PublicHeader.module.css'

function PublicHeader({ ctaLabel = 'ANSØG OM EN STAND', variant }) {
  return (
    <header className={`${styles.header}${variant === 'application' ? ` ${styles.application}` : ''}`}>
      <Link to="/" aria-label="Engestofte Gods – forsiden" className={styles.home}><Logo /></Link>
      <nav aria-label="Hovednavigation" className={styles.navigation}>
        <a href="https://www.engestofte.com/da/om-engestofte">Om godset</a>
        <a href="https://www.engestofte.com/da/julemarked">Julemarked</a>
        <NavLink to="/stadeholdere">Stadeholdere</NavLink>
        <NavLink to="/kontakt">Kontakt</NavLink>
      </nav>
      <div className={styles.actions}>
        {['instagram', 'facebook'].map((name) => (
          <button key={name} type="button" aria-disabled="true" aria-label={name === 'instagram' ? 'Instagram' : 'Facebook'} className={styles.social}><Icon name={name} /></button>
        ))}
        <Button to="/ansoegning" variant="compact">{ctaLabel}</Button>
      </div>
    </header>
  )
}

export default PublicHeader
