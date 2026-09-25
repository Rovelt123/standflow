import styles from './Button.module.css'

function Button({ children, variant = 'outline' }) {
  return (
    <button type="button" aria-disabled="true" className={`${styles.button} ${styles[variant]}`}>
      {children}
    </button>
  )
}

export default Button
