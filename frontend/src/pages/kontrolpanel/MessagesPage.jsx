import { useEffect, useRef, useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import { portalRequest, uploadFiles, downloadFile } from '../portal/portalApi.js'
import FileUpload from '../portal/FileUpload.jsx'
import shell from './KontrolpanelPage.module.css'
import styles from './MessagesPage.module.css'

export default function MessagesPage() {
  const [threads, setThreads] = useState([])
  const [sort, setSort] = useState('date')
  const [search, setSearch] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [messages, setMessages] = useState([])
  const [body, setBody] = useState('')
  const [loading, setLoading] = useState(true)
  const [opening, setOpening] = useState(false)
  const [sending, setSending] = useState(false)
  const [listError, setListError] = useState('')
  const [chatError, setChatError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const [files, setFiles] = useState([])
  const [attachmentIds, setAttachmentIds] = useState([])
  const [uploading, setUploading] = useState(false)
  const [downloads, setDownloads] = useState({})
  const downloadsInFlight = useRef(new Set())

  //--------------------------------------------------------------

  async function handleDownload(file) {
    if (downloadsInFlight.current.has(file.id)) return
    downloadsInFlight.current.add(file.id)
    setDownloads(current => ({ ...current, [file.id]: { loading: true, error: '' } }))
    try {
      await downloadFile(file.id, file.originalName)
    } catch (error) {
      setDownloads(current => ({ ...current, [file.id]: { loading: true, error: error.message } }))
    } finally {
      downloadsInFlight.current.delete(file.id)
      setDownloads(current => ({ ...current, [file.id]: { ...current[file.id], loading: false } }))
    }
  }

  const selected = threads.find(thread => thread.customerId === selectedId)

  useEffect(() => {
    let active = true
    setLoading(true)
    setListError('')

    portalRequest(`/messages/threads?sort=${sort}`)
      .then(data => {
        if (active) setThreads(data)
      })
      .catch(error => {
        if (active) setListError(error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [sort, refreshKey])

  useEffect(() => {
    if (!selectedId) return

    let active = true
    setOpening(true)
    setChatError('')
    setMessages([])

    async function openConversation() {
      try {
        const data = await portalRequest(`/messages/threads/${selectedId}`)
        if (!active) return

        setMessages(data)

        await portalRequest(`/messages/threads/${selectedId}/read`, 'PATCH')
        if (!active) return

        setThreads(current => current.map(thread =>
          thread.customerId === selectedId
            ? { ...thread, unread: false }
            : thread
        ))
      } catch (error) {
        if (active) setChatError(error.message)
      } finally {
        if (active) setOpening(false)
      }
    }

    openConversation()
    return () => { active = false }
  }, [selectedId, refreshKey])

  async function sendReply(event) {
    event.preventDefault()
    if (!selectedId || sending || opening || !body.trim()) return

    setSending(true)
    setChatError('')

    try {
      let ids = attachmentIds
      if (files.length && !ids.length) {
        setUploading(true)
        ids = (await uploadFiles(files)).map(file => file.id)
        setAttachmentIds(ids)
        setUploading(false)
      }
      await portalRequest(`/messages/threads/${selectedId}`, 'POST', { body,
        ...(ids.length ? { attachmentIds: ids } : {}) })
      setBody('')
      setFiles([])
      setAttachmentIds([])
      setRefreshKey(current => current + 1)
    } catch (error) {
      setChatError(error.message)
    } finally {
      setUploading(false)
      setSending(false)
    }
  }

  const query = search.trim().toLocaleLowerCase('da-DK')
  const visibleThreads = threads.filter(thread =>
    [thread.customerName, thread.company, thread.subject, thread.lastMessage]
      .some(value => (value || '').toLocaleLowerCase('da-DK').includes(query))
  )

  return (
    <div className={shell.page}>
      <AdminTopBar title="Beskeder" />
      <AdminSidebar />

      <main className={`${shell.main} ${styles.main}`}>
        <div className={styles.workspace}>
          <section className={styles.chatList} aria-label="Kundesamtaler">
            <h1>CHATS</h1>

            <input
              className={styles.search}
              aria-label="Søg i samtaleoversigten"
              placeholder="Søg efter navn, virksomhed eller seneste besked"
              value={search}
              onChange={event => setSearch(event.target.value)}
            />

            <div className={styles.tools}>
              <label>
                Sortér efter
                <select
                  value={sort}
                  onChange={event => setSort(event.target.value)}
                >
                  <option value="date">Seneste besked</option>
                  <option value="name">Navn</option>
                  <option value="company">Virksomhed</option>
                  <option value="subject">Emne</option>
                </select>
              </label>

              <button
                type="button"
                disabled={loading || sending || opening}
                onClick={() => setRefreshKey(current => current + 1)}
              >
                Opdater
              </button>
            </div>

            {loading && <p role="status">Henter samtaler…</p>}
            {listError && <p role="alert">{listError}</p>}

            <ul className={styles.threadList}>
              {visibleThreads.map(thread => (
                <li key={thread.customerId}>
                  <button
                    type="button"
                    className={`${styles.thread} ${
                      selectedId === thread.customerId ? styles.selected : ''
                    }`}
                    aria-pressed={selectedId === thread.customerId}
                    disabled={sending}
                    onClick={() => {
                      if (selectedId !== thread.customerId) {
                        setBody('')
                        setFiles([])
                        setAttachmentIds([])
                        setSelectedId(thread.customerId)
                      }
                    }}
                  >
                    <span className={styles.threadHeading}>
                      <strong>{thread.customerName}</strong>
                      {thread.unread && (
                        <span className={styles.unread} aria-label="Ulæste beskeder" />
                      )}
                    </span>
                    <span>{thread.company}</span>
                    <span className={styles.preview}>{thread.subject}</span>
                    <small>
                      {new Date(thread.lastMessageAt).toLocaleString('da-DK')}
                    </small>
                  </button>
                </li>
              ))}
            </ul>

            {!loading && !listError && visibleThreads.length === 0 && (
              <p>Ingen samtaler fundet.</p>
            )}
          </section>

          <section className={styles.conversation} aria-label="Valgt samtale">
            <p className={styles.chatTitle}>
              {selected
                ? `CHAT MED ${selected.customerName.toLocaleUpperCase('da-DK')}`
                : 'VÆLG EN SAMTALE'}
            </p>

            <div className={styles.chat}>
              <div className={styles.messages}>
                {!selectedId && <p>Vælg en kunde i listen til venstre.</p>}
                {opening && <p role="status">Henter beskeder…</p>}

                {!opening && messages.map(message => {
                  const fromCustomer = message.senderId === selectedId

                  return (
                    <article
                      key={message.id}
                      className={`${styles.message} ${
                        fromCustomer ? styles.customer : styles.admin
                      }`}
                    >
                      <small>
                        {fromCustomer ? selected?.customerName : 'ENGESTOFTE GODS'}
                        {' · '}
                        {new Date(message.createdAt).toLocaleString('da-DK')}
                      </small>

                      <div className={styles.bubble}>
                        <strong>{message.subject}</strong>
                        <p>{message.body}</p>
                        {message.attachments?.length > 0 && (
                          <ul className={styles.attachments} aria-label="Vedhæftede filer">
                            {message.attachments.map(file => (
                              <li key={file.id}>
                                <button type="button" className={styles.attachmentLink}
                                  disabled={downloads[file.id]?.loading} aria-busy={downloads[file.id]?.loading || undefined}
                                  onClick={() => handleDownload(file)}>{file.originalName}</button>
                                {downloads[file.id]?.loading && <span role="status"> Forbereder download…</span>}
                                {downloads[file.id]?.error && <span className={styles.downloadError} role="alert"> {downloads[file.id].error}</span>}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </article>
                  )
                })}
              </div>

              {chatError && <p role="alert">{chatError}</p>}

              <form className={styles.composer} onSubmit={sendReply}>
                {uploading && <p className={styles.uploadStatus} role="status">Uploader filer…</p>}
                <input
                  aria-label="Svar til kunden"
                  placeholder="Indtast besked"
                  value={body}
                  onChange={event => setBody(event.target.value)}
                  disabled={!selectedId || opening || sending}
                  required
                  maxLength={5000}
                />
                <FileUpload files={files} disabled={!selectedId || opening || sending} onError={setChatError}
                  onChange={selected => { setFiles(selected); setAttachmentIds([]) }} />
                <button
                  type="submit"
                  aria-label="Send svar"
                  disabled={!selectedId || opening || sending || !body.trim()}
                >
                  {sending ? '…' : '➤'}
                </button>
              </form>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
