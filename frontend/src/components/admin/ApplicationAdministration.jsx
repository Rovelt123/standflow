import { useState } from 'react'
import { portalRequest } from '../../pages/portal/portalApi.js'
import styles from './ApplicationDetail.module.css'

export default function ApplicationAdministration({ application, onUpdated, onHistorySelect, currentYear }) {
  const [standType, setStandType] = useState(application.standType)
  const [year, setYear] = useState(application.marketYear ?? '')
  const [attended, setAttended] = useState(application.attended == null ? '' : String(application.attended))
  const [location, setLocation] = useState(application.standLocation ?? '')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  //--------------------------------------------------------------

  async function save(event, history) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      if (history) {
        const updated = await portalRequest(`/applications/${application.id}/history`, 'PUT', {
          marketYear: year === '' ? null : Number(year),
          attended: attended === '' ? null : attended === 'true',
          standLocation: location.trim() || null,
        })
        onUpdated(updated)
        setMessage('Markedsår og historik er gemt.')
      } else {
        const result = await portalRequest(`/applications/${application.id}/stand-type`, 'PATCH', { standType })
        onUpdated(result.application)
        if (result.notificationStatus === 'FAILED') {
          setError('Standtypen er gemt, men mailen kunne ikke sendes. Kontakt kontaktpersonen manuelt; et nyt klik sender ikke mailen igen.')
        } else {
          setMessage(result.notificationStatus === 'SENT' ? 'Standtypen er gemt, og mailen er sendt.' : 'Standtypen var uændret. Ingen mail er sendt.')
        }
      }
    } catch (caught) {
      setError(caught.message)
    } finally {
      setBusy(false)
    }
  }

  //--------------------------------------------------------------

  return (
    <section className={styles.administration} aria-label="Administration af ansøgning">
      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}
      <form onSubmit={event => save(event, false)}>
        <fieldset disabled={busy}>
          <legend>Ændr standtype</legend>
          <label htmlFor="admin-stand-type">Standtype </label>
          <select id="admin-stand-type" value={standType} onChange={event => setStandType(event.target.value)}>
            {'ABCDEFGH'.split('').map(type => <option key={type} value={type}>Type {type}</option>)}
          </select>
          <button type="submit" disabled={standType === application.standType}>Gem</button>
        </fieldset>
      </form>
      <h3>Bekræftet tidligere deltagelse</h3>
      {application.history?.length ? (
        <ul>{application.history.map(previous => (
          <li key={previous.id}>
            {previous.marketYear} · {previous.company} · Type {previous.standType} · Plads: {previous.standLocation || 'Ukendt'}{' '}
            <button type="button" disabled={busy} onClick={() => onHistorySelect(previous.id)}>Ret historik</button>
          </li>
        ))}</ul>
      ) : <p>Ukendt – ingen bekræftet tidligere deltagelse registreret.</p>}
      <form onSubmit={event => save(event, true)}>
        <fieldset disabled={busy}>
          <legend>Registrér denne ansøgnings markedsår og historik</legend>
          <p>Ældre ansøgninger findes under “Ukendt år”. Bekræft kun faktisk deltagelse. Historisk plads tildeler ikke en plads i årets marked.</p>
          <label htmlFor="history-year">Markedsår </label>
          <input id="history-year" type="number" min="1900" max="9999" step="1" value={year}
            onChange={event => { setYear(event.target.value); setAttended(''); setLocation('') }} placeholder="Ukendt" />
          <label htmlFor="history-attended">Faktisk deltagelse </label>
          <select id="history-attended" value={attended} disabled={!year || Number(year) >= currentYear}
            onChange={event => { setAttended(event.target.value); if (event.target.value !== 'true') setLocation('') }}>
            <option value="">Ukendt</option>
            <option value="true">Deltog</option>
            <option value="false">Deltog ikke</option>
          </select>
          <label htmlFor="history-location">Historisk standplads </label>
          <input id="history-location" maxLength={200} disabled={attended !== 'true'} value={location}
            onChange={event => setLocation(event.target.value)} placeholder="Ukendt" />
          <button type="submit">Gem år og historik</button>
        </fieldset>
      </form>
    </section>
  )
}
