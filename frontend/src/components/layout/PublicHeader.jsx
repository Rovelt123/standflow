import Button from '../ui/Button.jsx'
import Icon from '../ui/Icon.jsx'
import styles from './PublicHeader.module.css'

const navigation = ['Om godset', 'Julemarked', 'Stadeholdere', 'Kontakt']

function PublicHeader({ activeItem, ctaLabel = 'ANSØG OM EN STAND' }) {
  return (
    <header className={styles.header}>
      <button type="button" aria-disabled="true" aria-label="Engestofte Gods" className={styles.logo}>
        <span>Engestofte</span><span>Gods</span>
      </button>
      <nav aria-label="Hovednavigation" className={styles.navigation}>
        {navigation.map((item) => (
          <button key={item} type="button" aria-disabled="true" aria-current={item === activeItem ? 'page' : undefined}>{item}</button>
        ))}
      </nav>
      <div className={styles.actions}>
        {['instagram', 'facebook'].map((name) => (
          <button key={name} type="button" aria-disabled="true" aria-label={name === 'instagram' ? 'Instagram' : 'Facebook'} className={styles.social}><Icon name={name} /></button>
        ))}
        <Button variant="compact">{ctaLabel}</Button>
      </div>
    </header>
  )
}

export default PublicHeader
