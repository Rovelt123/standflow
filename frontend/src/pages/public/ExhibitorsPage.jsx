import { Link } from 'react-router-dom'
import crafts from '../../assets/images/danish-crafts.jpg'
import ornaments from '../../assets/images/christmas-ornaments.jpg'
import treats from '../../assets/images/christmas-treats.jpg'
import styles from './PublicPages.module.css'

const categories = [
  { title: 'Kunsthåndværk', text: 'Håndlavede detaljer og særlige gaver til dem, du holder af.', image: crafts },
  { title: 'Julepynt', text: 'Inspiration til julens hyggelige kroge og det pyntede juletræ.', image: ornaments },
  { title: 'Julens smage', text: 'Søde fristelser og delikatesser til juletiden.', image: treats },
]

export default function ExhibitorsPage() {
  return <><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Jul på Engestofte Gods</p>
    <h1>Mød vores stadeholdere</h1>
    <p className={styles.intro}>Glæd dig til et julemarked med håndværk, julepynt og gode smagsoplevelser. Årets stadeholdere bliver præsenteret her, når listen er klar.</p>
    <p className={styles.notice}>Herunder finder du inspiration til markedets univers – årets stadeholderliste offentliggøres senere.</p>
    <div className={styles.cards}>{categories.map(category => <article className={styles.card} key={category.title}>
      <img src={category.image} alt={category.title} /><div><h2>{category.title}</h2><p>{category.text}</p></div>
    </article>)}</div>
    <Link className={styles.link} to="/ansoegning">Bliv stadeholder</Link>
  </div></main></>
}
