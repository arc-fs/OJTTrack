import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import { formatMinutes, toHHmm } from './format.js'

const MAX_FEEDBACK = 500   // time_logs.feedback VARCHAR(500)

/**
 * Coordinator review queue: PENDING logs from your students' active placements.
 * Feedback drafts live in their own state (keyed by log id), separate from the loaded
 * queue, so reloading the queue (after a 409, or when the tab becomes active) never
 * deletes what the coordinator typed.
 */
function ReviewQueue({ active }) {
    const [queue, setQueue] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [reloadToken, setReloadToken] = useState(0)
    const [drafts, setDrafts] = useState({})        // logId -> feedback text
    const [returningId, setReturningId] = useState(null)
    const [busyId, setBusyId] = useState(null)
    const [message, setMessage] = useState(null)    // { kind: 'status' | 'alert', text }

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        apiFetch('/api/coordinator/time-logs')
            .then((items) => {
                if (cancelled) return
                setQueue(items)
                setLoadError(null)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [active, reloadToken])

    const reload = () => setReloadToken((t) => t + 1)

    function setDraft(id, text) {
        setDrafts((prev) => ({ ...prev, [id]: text }))
    }

    function dropDraft(id) {
        setDrafts((prev) => {
            const next = { ...prev }
            delete next[id]
            return next
        })
    }

    async function decide(item, action) {
        const id = item.log.id
        const feedback = (drafts[id] ?? '').trim()
        if (action === 'return' && !feedback) {
            setMessage({ kind: 'alert', text: 'Write feedback so the student knows what to fix.' })
            return
        }
        setBusyId(id)
        setMessage(null)
        try {
            const body = action === 'return'
                ? { version: item.log.version, feedback }
                : { version: item.log.version }
            await apiJson(`/api/coordinator/time-logs/${id}/${action}`, 'POST', body)
            setQueue((prev) => prev.filter((q) => q.log.id !== id))
            dropDraft(id)
            setReturningId(null)
            setMessage({
                kind: 'status',
                text: `${action === 'return' ? 'Returned' : 'Verified'} ${item.studentName}'s log for ${item.log.logDate}.`,
            })
        } catch (err) {
            // The server's message; on 409 (stale or already reviewed) reload, keeping the draft.
            setMessage({ kind: 'alert', text: err.message })
            if (err.status === 409) reload()
        } finally {
            setBusyId(null)
        }
    }

    const queuedIds = new Set(queue.map((q) => q.log.id))
    const orphanDrafts = Object.entries(drafts).filter(([id, text]) => !queuedIds.has(Number(id)) && text.trim())

    if (loading) return <p className="loading" role="status">Loading…</p>
    if (loadError) return <p role="alert">Could not load the review queue: {loadError}</p>

    return (
        <div>
            <h1>Review queue</h1>
            {message && <p role={message.kind}>{message.text}</p>}

            {queue.length === 0 ? <p className="empty">Nothing to review.</p> : (
                // card-list / card-title / card-actions only change the phone layout
                <ul className="card-list">
                    {queue.map((item) => {
                        const { log } = item
                        return (
                            <li key={log.id}>
                                <p>
                                    <strong className="card-title">{item.studentName}</strong> · {item.companyName} · {log.logDate}{' '}
                                    {toHHmm(log.timeIn)}–{toHHmm(log.timeOut)}{log.endsNextDay && ' (+1 day)'}
                                    {log.breakMinutes > 0 && <> · break {log.breakMinutes} min</>}
                                    {' '}· <strong>{formatMinutes(log.workedMinutes)}</strong>
                                </p>
                                <p>{log.accomplishments}</p>
                                {log.feedback && <p><em>Previous feedback: {log.feedback}</em></p>}

                                <div className="card-actions">
                                    <button type="button" data-variant="primary" disabled={busyId !== null}
                                            onClick={() => decide(item, 'verify')}>
                                        Verify
                                    </button>{' '}
                                    {returningId !== log.id && (
                                        <button type="button" disabled={busyId !== null}
                                                onClick={() => setReturningId(log.id)}>
                                            Return…
                                        </button>
                                    )}

                                    {returningId === log.id && (
                                        <div>
                                            <label htmlFor={`feedback-${log.id}`}>Feedback for the student</label>
                                            <textarea id={`feedback-${log.id}`} maxLength={MAX_FEEDBACK}
                                                      value={drafts[log.id] ?? ''}
                                                      onChange={(e) => setDraft(log.id, e.target.value)} />
                                            <button type="button"
                                                    disabled={busyId !== null || !(drafts[log.id] ?? '').trim()}
                                                    onClick={() => decide(item, 'return')}>
                                                Return with feedback
                                            </button>{' '}
                                            <button type="button" data-variant="quiet" onClick={() => setReturningId(null)}>Cancel</button>
                                            <span> (your draft is kept)</span>
                                        </div>
                                    )}
                                </div>
                            </li>
                        )
                    })}
                </ul>
            )}

            {orphanDrafts.length > 0 && (
                <section aria-label="Kept drafts">
                    <h2>Kept drafts</h2>
                    {orphanDrafts.map(([id, text]) => (
                        <div key={id}>
                            <p>This log changed or was already reviewed. Your draft is kept below.</p>
                            <textarea readOnly value={text} />
                            <button type="button" data-variant="quiet" onClick={() => dropDraft(Number(id))}>Dismiss</button>
                        </div>
                    ))}
                </section>
            )}
        </div>
    )
}

export default ReviewQueue
