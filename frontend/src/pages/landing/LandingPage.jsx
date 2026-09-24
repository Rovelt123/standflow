import PublicHeader from '../../components/layout/PublicHeader.jsx'
import Button from '../../components/ui/Button.jsx'
import Icon from '../../components/ui/Icon.jsx'
import styles from './LandingPage.module.css'
import courtyardImage from '../../assets/images/christmas-courtyard.jpg'
import craftsImage from '../../assets/images/danish-crafts.jpg'
import ornamentsImage from '../../assets/images/christmas-ornaments.jpg'
import manorImage from '../../assets/images/manor-by-the-lake.jpg'

const steps = [
  { title: 'Ansøg online', icon: 'clipboard', text: 'Udfyld vores detaljerede ansøgningsformular med information om dine produkter, billeder af din stand og dine behov for strøm eller inventar.' },
  { title: 'Bliv godkendt', icon: 'medal', text: 'Vores juleudvalg vurderer alle ansøgninger løbende for at sikre et varieret kvalitetssortiment, der passer perfekt til godsets historiske rammer.' },
  { title: 'Book din plads', icon: 'shop', text: 'Når din ansøgning er godkendt, modtager du et direkte link til endelig booking, betaling og valg af din præcise stand på plantegningen.' },
]

const experiences = [
  { label: '80+ UNIKKE BODER', title: 'Dansk Kunsthåndværk', image: craftsImage, width: 365, height: 547, alt: 'Stofnisser med røde huer og stribede ben på et julemarked' },
  { label: 'LOKALE RÅVARER', title: 'Smuk Julepynt', image: ornamentsImage, width: 547, height: 365, alt: 'Julepynt og nøddeknækkere på en indendørs markedsbod' },
  { label: 'VED MARIBO SØNDERSØ', title: 'Ægte Herregårdsjul', image: manorImage, width: 1376, height: 768, alt: 'Oplyst herregård ved en sø og gæster omkring et bål' },
]

function LandingPage() {
  return (
    <>
      <PublicHeader />
      <main>
        <section className={styles.hero} aria-labelledby="landing-title">
          <img className={styles.heroImage} src={courtyardImage} alt="Sneklædt gårdsplads med bindingsværk, lyskæder og et stort oplyst juletræ" fetchPriority="high" />
          <div className={styles.introduction}>
            <p className={styles.date}>5.–6. DECEMBER 2026</p>
            <h1 id="landing-title">Julemarked på Engestofte Gods</h1>
            <p className={styles.description}>Træd ind i en fortryllende verden af ægte herregårdsjul. Over to dage, fredag–lørdag kl. 10:00–16:00, fyldes godsets historiske lader og gårdsplads med duften af brændte mandler, gran og lune æbleskiver. Oplev over 80 unikke boder med dansk kunsthåndværk, lokale råvarer og smuk julepynt ved bredden af Maribo Søndersø.</p>
            <div className={styles.buttons}>
              <Button variant="primary">ANSØG OM EN STAND</Button>
              <Button>SE PLANTEGNING</Button>
            </div>
          </div>
        </section>
        <section className={styles.process} aria-label="Sådan bliver du stadeholder">
          <ol className={styles.steps}>
            {steps.map((step, index) => (
              <li key={step.title} className={styles.step}>
                <span className={styles.icon}><Icon name={step.icon} /></span>
                <div>
                  <p className={styles.label}>TRIN {index + 1}</p>
                  <h2>{step.title}</h2>
                  <p className={styles.stepText}>{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
        <section className={styles.experiences} aria-label="Oplevelser på julemarkedet">
          {experiences.map((experience) => (
            <article key={experience.title} className={styles.card}>
              <img className={styles.cardImage} src={experience.image} alt={experience.alt} loading="lazy" width={experience.width} height={experience.height} />
              <div className={styles.cardText}>
                <p className={styles.label}>{experience.label}</p>
                <h2>{experience.title}</h2>
              </div>
            </article>
          ))}
        </section>
      </main>
    </>
  )
}

export default LandingPage
