import PublicHeader from '../../components/layout/PublicHeader.jsx'
import styles from './ApplicationPage.module.css'

const companyFields = [
  { id: 'company', label: 'Virksomhedsnavn', value: 'Nordic Winter Craft ApS', fullWidth: true },
  { id: 'contact', label: 'Kontaktperson', value: 'Anna Hansen' },
  { id: 'cvr', label: 'CVR-nummer', value: '87654321' },
  { id: 'email', label: 'E-mailadresse', value: 'kontakt@nordicwintercraft.dk', fullWidth: true },
  { id: 'phone', label: 'Telefonnummer', value: '+45 88 88 88 88', fullWidth: true },
  { id: 'address', label: 'Adresse', value: 'Bredgade 12', fullWidth: true },
  { id: 'city', label: 'Postnr. & By', value: '4900 Maribo' },
  { id: 'website', label: 'Website', value: 'www.nordicwintercraft.dk', optional: true },
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
  { id: 'tables', label: 'Antal borde', price: '+155 kr/stk', item: 'borde', quantity: 2 },
  { id: 'chairs', label: 'Antal stole', price: '+45 kr/stk', item: 'stole', quantity: 4 },
]

function ApplicationPage() {
  return (
    <>
      <PublicHeader activeItem="Stadeholdere" variant="application" />
      <main className={styles.page}>
        <div className={styles.container}>
          <div className={styles.columns}>
            <div className={styles.companyColumn}>
              <div className={styles.introduction}>
                <h1 id="application-title">Stadeholder information</h1>
                <p>Udfyld formularen nedenfor med dine virksomhedsoplysninger og stand-ønsker.</p>
              </div>
              <section className={styles.company} aria-labelledby="company-title">
                <h2 id="company-title">1. Virksomhed &amp; kontakt</h2>
                <div className={styles.fields}>
                  {companyFields.map((field) => (
                    <div key={field.id} className={field.fullWidth ? styles.fullWidth : styles.field}>
                      <label htmlFor={field.id}>{field.label}{!field.optional && ' *'}</label>
                      <input id={field.id} type="text" value={field.value} readOnly aria-required={!field.optional} />
                    </div>
                  ))}
                  <div className={styles.fullWidth}>
                    <label htmlFor="products">Beskrivelse af dine produkter *</label>
                    <textarea id="products" readOnly aria-required="true" value="Håndlavede uldne sweatre, filtede nisser og fine juledekorationer lavet af naturlige materialer fra den nordiske skov." />
                  </div>
                </div>
                <label className={styles.previousExhibitor}>
                  <input type="checkbox" checked disabled />
                  <span>Vi har tidligere været stadeholder på Engestofte Julemarked</span>
                </label>
              </section>
            </div>
            <div className={styles.standColumn}>
              <section className={styles.stands} aria-labelledby="stand-title">
                <h2 id="stand-title">2. Vælg standtype</h2>
                <div className={styles.standList} role="group" aria-label="Standtyper – inaktivt valg">
                  {standTypes.map((stand) => (
                    <button key={stand.id} type="button" aria-disabled="true" className={styles.stand}>
                      <span className={styles.letter}>{stand.id}</span>
                      <span className={styles.standInformation}>
                        <span className={styles.standTitle}>{stand.title}</span>
                        <span className={styles.dimensions}>{stand.dimensions}</span>
                      </span>
                      <span className={styles.price}>{stand.price} kr <span>+ moms</span></span>
                    </button>
                  ))}
                </div>
                <div className={styles.furnishings}>
                  {furnishings.map((furnishing) => (
                    <div key={furnishing.id} className={styles.furnishing} role="group" aria-labelledby={furnishing.id}>
                      <p id={furnishing.id}>{furnishing.label} <span>({furnishing.price})</span></p>
                      <div className={styles.quantity}>
                        <button type="button" aria-disabled="true" aria-label={`Færre ${furnishing.item}`}>−</button>
                        <span aria-label={`${furnishing.quantity} ${furnishing.item}`}>{furnishing.quantity}</span>
                        <button type="button" aria-disabled="true" aria-label={`Flere ${furnishing.item}`}>+</button>
                      </div>
                    </div>
                  ))}
                </div>
              </section>
              <div className={styles.actions}>
                <button type="button" aria-disabled="true" className={styles.cancel}>Annuller</button>
                <button type="button" aria-disabled="true" className={styles.send}>Send ansøgning</button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </>
  )
}

export default ApplicationPage
