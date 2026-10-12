import { useRef } from 'react'
import { validateSelectedFiles } from './portalApi.js'
import styles from './FileUpload.module.css'

export default function FileUpload({ files, onChange, onError, disabled }) {
  const dialog = useRef(null)
  const picker = useRef(null)

  //--------------------------------------------------------------

  function selectFiles(event) {
    const selected = Array.from(event.target.files || [])
    event.target.value = ''
    if (!selected.length) return
    try {
      validateSelectedFiles(selected)
      onChange(selected)
      onError('')
      dialog.current.close()
    } catch (error) {
      onError(error.message)
      dialog.current.close()
    }
  }

  return (
    <>
      <button type="button" aria-label="Vedhæft filer" title="Vedhæft op til 3 filer"
        disabled={disabled} onClick={() => dialog.current.showModal()}>
        +
      </button>
      <dialog ref={dialog} className={styles.dialog} aria-labelledby="upload-title">
        <h2 id="upload-title">Vedhæft filer</h2>
        <p>PDF, PNG, JPG eller JPEG. Maks. 1 MiB (1.048.576 bytes) pr. fil og højst 3 filer pr. besked.</p>
        <p>Du kan vælge alle 3 filer samtidig. Filerne uploades, når du trykker Send.</p>
        <input ref={picker} type="file" multiple accept=".pdf,.png,.jpg,.jpeg"
          aria-label="Vælg op til 3 filer" hidden onChange={selectFiles} disabled={disabled} />
        <div className={styles.actions}>
          <button className={styles.action} type="button" disabled={disabled} onClick={() => picker.current.click()}>
            Vælg filer
          </button>
          <button className={styles.action} type="button" onClick={() => dialog.current.close()}>Luk</button>
        </div>
      </dialog>
      {files.length > 0 && (
        <ul className={styles.selected} aria-label="Valgte filer">
          {files.map((file, index) => (
            <li key={`${file.name}-${index}`}>
              <span>{file.name}</span>
              <button className={styles.remove} type="button" disabled={disabled}
                aria-label={`Fjern ${file.name}`} onClick={() => onChange(files.filter((_, current) => current !== index))}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
