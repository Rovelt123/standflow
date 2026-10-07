import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchVendors } from './vendorsApi.js'
import styles from './PublicPages.module.css'

export default function ExhibitorsPage() {
  const [page, setPage] = useState(1)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')

    fetchVendors(page)
      .then(result => { if (active) setData(result) })
      .catch(caught => { if (active) setError(caught.message) })
      .finally(() => { if (active) setLoading(false) })

    return () => { active = false }
  }, [page])

  const vendors = data?.vendors ?? []
  const total = data?.total ?? 0
  const pageSize = data?.pageSize ?? 10
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const current = data?.page ?? page

  return <><main className={styles.page}><div className={styles.container}>
    <p className={styles.eyebrow}>Jul på Engestofte Gods</p>
    <h1>Mød vores stadeholdere</h1>
    <p className={styles.intro}>Her finder du årets accepterede stadeholdere – hvem de er, og hvad de sælger på julemarkedet.</p>

    {loading && <p role="status">Henter stadeholdere …</p>}
    {error && <p className={styles.notice} role="alert">{error}</p>}

    {!loading && !error && total === 0 && (
      <p className={styles.notice}>Årets stadeholderliste er endnu ikke klar – den offentliggøres her, så snart stadeholderne er på plads.</p>
    )}

    {!loading && !error && vendors.length > 0 && (
      <div className={styles.cards}>{vendors.map(vendor => (
        <article className={styles.card} key={`${vendor.company}-${vendor.standType}`}>
          <div>
            <h2>{vendor.company}</h2>
            <p>{vendor.description}</p>
            {vendor.website && <p><a href={vendor.website} target="_blank" rel="noreferrer">{vendor.website}</a></p>}
          </div>
        </article>
      ))}</div>
    )}

    {!loading && !error && totalPages > 1 && (
      <nav className={styles.pagination} aria-label="Sider med stadeholdere">
        <button type="button" onClick={() => setPage(p => Math.max(1, p - 1))} disabled={current <= 1}>Forrige</button>
        <span>Side {current} af {totalPages}</span>
        <button type="button" onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={current >= totalPages}>Næste</button>
      </nav>
    )}

    <Link className={styles.link} to="/ansoegning">Bliv stadeholder</Link>
  </div></main></>
}
