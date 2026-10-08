import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { submitApplication } from './applicationApi.js'
import { getCurrentUser } from '../public/authApi.js'
import { prefillApplication } from './applicationDefaults.js'
import { portalRequest } from '../portal/portalApi.js'
import styles from './ApplicationPage.module.css'

const companyFields = [
  { id: 'company', label: 'Virksomhedsnavn', maxLength: 200, fullWidth: true, autoComplete: 'organization' },
  { id: 'contact', label: 'Kontaktperson', maxLength: 150, autoComplete: 'name' },
  { id: 'cvr', label: 'CVR-nummer', maxLength: 8, pattern: '[0-9]{8}', inputMode: 'numeric', title: 'Præcis 8 cifre' },
  { id: 'email', label: 'E-mailadresse', maxLength: 254, type: 'email', fullWidth: true, autoComplete: 'email' },
  { id: 'phone', label: 'Telefonnummer', maxLength: 30, type: 'tel', fullWidth: true, autoComplete: 'tel' },
  { id: 'address', label: 'Adresse', maxLength: 255, fullWidth: true, autoComplete: 'street-address' },
  { id: 'city', label: 'Postnr. & By', maxLength: 150, pattern: '[0-9]{4} +.*\\S.*', title: 'Postnummer og by, fx 4900 Maribo' },
  { id: 'website', label: 'Website', maxLength: 255, optional: true, placeholder: 'www.eksempel.dk' },
]

const standTypes = [
  { id: 'A', title: 'Udendørs', price: '985', dimensions: '3×4 m · uden el' },
  { id: 'B', title: 'Indendørs (langside), Kostalden', price: '1.685', dimensions: '3×3 m · med el' },
  { id: 'C', title: 'Indendørs (center), Kostalden', price: '1.895', dimensions: '3×3 m · med el' },
  { id: 'D', title: 'Indendørs, Hestestalden', price: '1.635', dimensions: 'ca. 3,3×3,3 m · med el' },
  { id: 'E', title: 'Indendørs (langside), Laden', price: '1.695', dimensions: '3×2,5 m · med el' },
  { id: 'F', title: 'Indendørs (center), Laden', price: '1.775', dimensions: '3×2,5 m · med el' },
  { id: 'G', title: 'Indendørs (center/hjørne), Laden', price: '1.995', dimensions: '3×2,5 m · med el' },
  { id: 'H', title: 'Indendørs (langside), Jagtstuen', price: '1.325', dimensions: '3×1,8 m · med el' },
]

const furnishings = [
  { id: 'tables', label: 'Antal borde', price: '+155 kr/stk', item: 'borde' },
  { id: 'chairs', label: 'Antal stole', price: '+45 kr/stk', item: 'stole' },
]

function ApplicationPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const [values, setValues] = useState(() => ({
    ...Object.fromEntries(companyFields.map(({ id }) => [id, ''])),
    products: '', previousExhibitor: false, standType: '', tables: 0, chairs: 0,
  }))
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [receipt, setReceipt] = useState(null)
  const submitting = useRef(false)
  const messageRef = useRef(null)
  const editedFields = useRef(new Set())
  const [profileError, setProfileError] = useState('')
  const [loadingApplication, setLoadingApplication] = useState(Boolean(id))
  const [editAllowed, setEditAllowed] = useState(false)

  useEffect(() => {
    let active = true
    if (id) {
      portalRequest(`/applications/mine/${id}`).then(application => {
        if (!active) return
        if (application.status !== 'INFO_REQUESTED') throw new Error('Denne ansøgning kan ikke redigeres lige nu.')
        setValues(current => Object.fromEntries(Object.keys(current).map(key => [key, application[key] ?? current[key]])))
        setEditAllowed(true)
      }).catch(failure => { if (active) setError(failure.message) })
        .finally(() => { if (active) setLoadingApplication(false) })
      return () => { active = false }
    }
    getCurrentUser().then(user => {
      if (active) setValues(current => prefillApplication(current, user, editedFields.current))
    }).catch(() => {
      if (active) setProfileError('Dine brugeroplysninger kunne ikke hentes. Du kan stadig udfylde felterne selv.')
    })
    return () => { active = false }
  }, [id])

  //--------------------------------------------------------------

  function changeValue(name, value) {
    editedFields.current.add(name)
    setValues(current => ({ ...current, [name]: value }))
  }

  //--------------------------------------------------------------

  function adjustQuantity(name, difference) {
    setValues(current => ({ ...current, [name]: Math.min(100, Math.max(0, Number(current[name]) + difference)) }))
  }

  //--------------------------------------------------------------

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting.current || (id && !editAllowed)) return
    submitting.current = true
    setSending(true)
    setError('')
    const application = Object.fromEntries(Object.entries(values).map(([name, value]) =>
      [name, typeof value === 'string' ? value.trim() : value]))
    application.website = application.website || null
    application.tables = Number(application.tables)
    application.chairs = Number(application.chairs)
    try {
      setReceipt(id ? await portalRequest(`/applications/mine/${id}`, 'PUT', application) : await submitApplication(application))
    } catch (failure) {
      setError(failure.message)
    } finally {
      submitting.current = false
      setSending(false)
      requestAnimationFrame(() => messageRef.current?.focus())
    }
  }

  //--------------------------------------------------------------

  return (
    <>
      
      <main className={styles.page}>
        <div className={styles.container}>
          {receipt ? (
            <section className={styles.receipt} role="status" tabIndex={-1} ref={messageRef}>
              <h1>{id ? 'Din ansøgning er opdateret' : 'Din ansøgning er modtaget'}</h1>
              <p>Ansøgningen afventer behandling. Din stand er endnu ikke booket.</p>
              <p>Ansøgningsnummer: <strong>{receipt.id}</strong></p>
              <p>Modtaget: <time dateTime={receipt.createdAt}>{receipt.createdAt.split('-').reverse().join('.')}</time></p>
              <Link to="/brugerportal">Tilbage til min brugerportal</Link>
            </section>
          ) : (
            <form onSubmit={handleSubmit} aria-labelledby="application-title" aria-busy={sending}>
              {error && <p className={styles.error} role="alert" tabIndex={-1} ref={messageRef}>{error}</p>}
              {profileError && <p role="status">{profileError}</p>}
              {loadingApplication && <p role="status">Henter din ansøgning…</p>}
              {id && <Link to="/brugerportal">Tilbage til min brugerportal</Link>}
              <fieldset className={styles.columns} disabled={sending || loadingApplication || (Boolean(id) && !editAllowed)}>
                <div className={styles.companyColumn}>
                  <div className={styles.introduction}>
                    <h1 id="application-title">{id ? 'Ret din ansøgning' : 'Stadeholder information'}</h1>
                    <p>Udfyld formularen nedenfor med dine virksomhedsoplysninger og stand-ønsker.</p>
                  </div>
                  <section className={styles.company} aria-labelledby="company-title">
                    <h2 id="company-title">1. Virksomhed &amp; kontakt</h2>
                    <div className={styles.fields}>
                      {companyFields.map((field) => (
                        <div key={field.id} className={field.fullWidth ? styles.fullWidth : styles.field}>
                          <label htmlFor={field.id}>{field.label}{!field.optional && ' *'}</label>
                          <input id={field.id} name={field.id} type={field.type || 'text'}
                            value={values[field.id]} onChange={event => changeValue(field.id, event.target.value)}
                            required={!field.optional} maxLength={field.maxLength} pattern={field.pattern}
                            inputMode={field.inputMode} title={field.title} autoComplete={field.autoComplete}
                            placeholder={field.placeholder} />
                        </div>
                      ))}
                      <div className={styles.fullWidth}>
                        <label htmlFor="products">Beskrivelse af dine produkter *</label>
                        <textarea id="products" name="products" required maxLength={5000}
                          value={values.products} onChange={event => changeValue('products', event.target.value)} />
                      </div>
                    </div>
                    <label className={styles.previousExhibitor}>
                      <input type="checkbox" name="previousExhibitor" checked={values.previousExhibitor}
                        onChange={event => changeValue('previousExhibitor', event.target.checked)} />
                      <span>Vi har tidligere været stadeholder på Engestofte Julemarked</span>
                    </label>
                  </section>
                </div>
                <div className={styles.standColumn}>
                  <section className={styles.stands} aria-labelledby="stand-title">
                    <h2 id="stand-title">2. Vælg standtype</h2>
                    <div className={styles.standList} role="group" aria-labelledby="stand-title">
                      {standTypes.map((stand) => (
                        <label key={stand.id} className={styles.stand}>
                          <input type="radio" name="standType" value={stand.id} required
                            checked={values.standType === stand.id} onChange={() => changeValue('standType', stand.id)} />
                          <span className={styles.letter}>{stand.id}</span>
                          <span className={styles.standInformation}>
                            <span className={styles.standTitle}>{stand.title}</span>
                            <span className={styles.dimensions}>{stand.dimensions}</span>
                          </span>
                          <span className={styles.price}>{stand.price} kr <span>+ moms</span></span>
                        </label>
                      ))}
                    </div>
                    <div className={styles.furnishings}>
                      {furnishings.map((furnishing) => (
                        <div key={furnishing.id} className={styles.furnishing}>
                          <label htmlFor={furnishing.id}>{furnishing.label} <span>({furnishing.price})</span></label>
                          <div className={styles.quantity}>
                            <button type="button" disabled={Number(values[furnishing.id]) <= 0}
                              onClick={() => adjustQuantity(furnishing.id, -1)} aria-label={`Færre ${furnishing.item}`}>−</button>
                            <input id={furnishing.id} name={furnishing.id} type="number" min={0} max={100} step={1} required
                              value={values[furnishing.id]} onChange={event => changeValue(furnishing.id, event.target.value)} />
                            <button type="button" disabled={Number(values[furnishing.id]) >= 100}
                              onClick={() => adjustQuantity(furnishing.id, 1)} aria-label={`Flere ${furnishing.item}`}>+</button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </section>
                  <div className={styles.actions}>
                    <button type="button" onClick={() => navigate('/brugerportal')} className={styles.cancel}>Annuller</button>
                    <button type="submit" className={styles.send}>{sending ? 'Sender ansøgning…' : id ? 'Send ændringer' : 'Send ansøgning'}</button>
                  </div>
                </div>
              </fieldset>
            </form>
          )}
        </div>
      </main>
    </>
  )
}

export default ApplicationPage
