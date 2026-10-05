import { Link } from 'react-router-dom'
import styles from './Button.module.css'

function Button({ children, variant = 'outline', to }) {
  const className = `${styles.button} ${styles[variant]}`
  if (to) return <Link to={to} className={className}>{children}</Link>
  return <button type="button" aria-disabled="true" className={className}>{children}</button>
}

export default Button
