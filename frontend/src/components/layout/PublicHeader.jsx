import { Link, NavLink } from 'react-router-dom'
import Button from '../ui/Button.jsx'
import Icon from '../ui/Icon.jsx'
import Logo from '../ui/Logo.jsx'
import styles from './PublicHeader.module.css'

function PublicHeader({ authenticated = false }) {
  return (
    <header className={styles.header}>
      <Link to="/" aria-label="Engestofte Gods – forsiden" className={styles.home}><Logo /></Link>
      <nav aria-label="Hovednavigation" className={styles.navigation}>
        <a href="https://www.engestofte.com/da/om-engestofte">Om godset</a>
        <a href="https://www.engestofte.com/da/julemarked">Julemarked</a>
        <NavLink to="/stadeholdere">Stadeholdere</NavLink>
        <NavLink to={authenticated ? '/brugerportal?tab=messages' : '/login'}>Kontakt</NavLink>
      </nav>
      <div className={styles.actions}>
        <a href="https://www.instagram.com/engestoftegods/" aria-label="Instagram" className={styles.social} target="_blank" rel="noopener noreferrer"><Icon name="instagram" /></a>
        <a href="https://www.facebook.com/Engestofte/" aria-label="Facebook" className={styles.social} target="_blank" rel="noopener noreferrer"><Icon name="facebook" /></a>
        {authenticated
          ? <NavLink to="/brugerportal" className={styles.profile} aria-label="Min brugerportal" title="Min brugerportal"><i className="fa-solid fa-user" aria-hidden="true"><Icon name="user" /></i></NavLink>
          : <Button to="/ansoegning" variant="compact">ANSØG OM EN STAND</Button>}
      </div>
    </header>
  )
}

export default PublicHeader
