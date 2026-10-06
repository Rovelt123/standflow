import { useEffect, useState } from 'react'
import { portalRequest } from './portalApi.js'
import styles from './CustomerChat.module.css'

export default function CustomerChat({ user }) {
  const [messages, setMessages] = useState([])
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [subject, setSubject] = useState('')
  const [sending, setSending] = useState(false)

  useEffect(() => {
    let active = true

    portalRequest('/messages/mine')
      .then(data => {
        if (active) setMessages(data)
      })
      .catch(failure => {
        if (active) setError(failure.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  async function sendMessage(event) {
  event.preventDefault()
  if (sending || !message.trim()) return

  const selectedSubject =
    messages[messages.length - 1]?.subject || subject.trim()

  if (!selectedSubject) {
    setError('Indtast et emne.')
    return
  }

  setSending(true)
  setError('')

  try {
    const saved = await portalRequest('/messages', 'POST', {
      subject: selectedSubject,
      body: message,
    })
    setMessages(current => [...current, saved])
    setMessage('')
  } catch (failure) {
    setError(failure.message)
  } finally {
    setSending(false)
  }
}

  if (loading) {
    return <p>Henter beskeder…</p>
  }

  return (
    <section className={styles.chat}>
    {error && <p role="alert">{error}</p>}
      <p className={styles.eyebrow}>Chat med Engestofte Gods</p>

      <div className={styles.messages}>
            {messages.length === 0 ? (
                <p>Der er ingen beskeder endnu.</p>
            ) : (
                messages.map(currentMessage => {
                const isMine = currentMessage.senderId === user?.id

                return (
                    <div
                    key={currentMessage.id}
                    className={`${styles.messageRow} ${isMine ? styles.mine : styles.theirs}`}
                    >
                    <div className={styles.meta}>
                        {isMine ? 'DIG' : 'ENGESTOFTE GODS'} ·{' '}
                        {new Date(currentMessage.createdAt).toLocaleString('da-DK')}
                    </div>

                    <div className={styles.bubble}>
                        {currentMessage.body}
                    </div>
                    </div>
                )
                })
            )}
        </div>

      <form className={styles.inputArea} onSubmit={sendMessage}>
        
        <input
            aria-label="Emne"
            placeholder="Emne"
            value={subject}
            onChange={event => setSubject(event.target.value)}
            required
            maxLength={200}
        />
        
        <input
          value={message}
          onChange={event => setMessage(event.target.value)}
          placeholder="Indtast besked"
          required
          maxLength={5000}
        />

        <button
            type="submit"
            aria-label="Send besked"
            disabled={sending || !message.trim()}
            >
            {sending ? '…' : '➤'}
            </button>
      </form>
    </section>
  )
}