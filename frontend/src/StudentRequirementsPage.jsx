import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import RequirementHistory from './RequirementHistory.jsx'
import { MAX_NOTE, canSubmit, dueBadge, statusLabel } from './requirements.js'

/**
 * Student Requirements tab (MVP §4.7): the requirements a coordinator assigned to me.
 * The server decides overdue / due soon and every rule; this page shows its answers.
 * Note drafts live in their own state (keyed by assignment id), separate from the loaded
 * list, so a reload (after a 409, or when the tab becomes active) never deletes them.
 */
function StudentRequirementsPage({ active }) {
    const [items, setItems] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [reloadToken, setReloadToken] = useState(0)
    const [showArchived, setShowArchived] = useState(false)
    const [notes, setNotes] = useState({})          // assignmentId -> note draft
    const [openId, setOpenId] = useState(null)      // whose submit form is open
    const [busyId, setBusyId] = useState(null)
    const [message, setMessage] = useState(null)    // { kind: 'status' | 'alert', text }

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        apiFetch(`/api/requirements?includeArchived=${showArchived}`)
            .then((result) => {
                if (cancelled) return
                setItems(result)
                setLoadError(null)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [active, reloadToken, showArchived])

    const reload = () => setReloadToken((t) => t + 1)

    function setNote(id, text) {
        setNotes((prev) => ({ ...prev, [id]: text }))
    }

    function dropNote(id) {
        setNotes((prev) => {
            const next = { ...prev }
            delete next[id]
            return next
        })
    }

    async function submit(item) {
        const id = item.assignmentId
        const note = (notes[id] ?? '').trim()
        setBusyId(id)
        setMessage(null)
        try {
            const updated = await apiJson(`/api/requirements/${id}/submit`, 'POST',
                { version: item.version, note: note || null })
            setItems((prev) => prev.map((i) => (i.assignmentId === id ? updated : i)))
            dropNote(id)
            setOpenId(null)
            setMessage({ kind: 'status', text: `Submitted "${item.title}". Your coordinator will review it.` })
        } catch (err) {
            // The server's message; on 409 (changed, archived or already submitted) reload, keeping the note.
            setMessage({ kind: 'alert', text: err.message })
            if (err.status === 409) reload()
        } finally {
            setBusyId(null)
        }
    }

    // Notes that can no longer be sent (the requirement changed or disappeared) stay visible until dismissed.
    const submittable = new Set(items.filter(canSubmit).map((i) => i.assignmentId))
    const keptNotes = Object.entries(notes).filter(([id, text]) => !submittable.has(Number(id)) && text.trim())

    if (loading) return <p>Loading...</p>
    if (loadError) return <p role="alert">Could not load your requirements: {loadError}</p>

    return (
        <div>
            <h1>Requirements</h1>
            <label>
                <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
                {' '}Show archived
            </label>
            {message && <p role={message.kind}>{message.text}</p>}

            {items.length === 0 ? <p>No requirements assigned yet.</p> : (
                <ul>
                    {items.map((item) => {
                        const id = item.assignmentId
                        const badge = dueBadge(item)
                        return (
                            <li key={id}>
                                <h2>
                                    {item.title}
                                    {item.archived && ' (Archived)'}
                                    {badge && <> <strong>[{badge}]</strong></>}
                                </h2>
                                {item.description && <p>{item.description}</p>}
                                <p>
                                    {item.dueDate ? <>Due {item.dueDate}</> : 'No due date'} · {statusLabel(item.status, item.archived)}
                                    {item.confirmedAt && <> on {new Date(item.confirmedAt).toLocaleDateString()}</>}
                                </p>
                                {item.status === 'RETURNED' && item.feedback && (
                                    <p role="note"><strong>Coordinator feedback:</strong> {item.feedback}</p>
                                )}
                                {item.submittedAt && (
                                    <p>
                                        Last submitted {new Date(item.submittedAt).toLocaleString()}
                                        {item.note && <>: {item.note}</>}
                                    </p>
                                )}

                                {canSubmit(item) && openId !== id && (
                                    <button type="button" disabled={busyId !== null} onClick={() => setOpenId(id)}>
                                        {item.status === 'RETURNED' ? 'Resubmit…' : 'Mark as submitted…'}
                                    </button>
                                )}
                                {canSubmit(item) && openId === id && (
                                    <div>
                                        <label htmlFor={`note-${id}`}>Note for your coordinator (optional)</label>
                                        <textarea id={`note-${id}`} maxLength={MAX_NOTE} value={notes[id] ?? ''}
                                                  onChange={(e) => setNote(id, e.target.value)} />
                                        <button type="button" disabled={busyId !== null} onClick={() => submit(item)}>
                                            Submit
                                        </button>{' '}
                                        <button type="button" onClick={() => setOpenId(null)}>Cancel</button>
                                        <span> (your note is kept)</span>
                                    </div>
                                )}

                                <RequirementHistory history={item.history} />
                            </li>
                        )
                    })}
                </ul>
            )}

            {keptNotes.length > 0 && (
                <section aria-label="Kept notes">
                    <h2>Kept notes</h2>
                    {keptNotes.map(([id, text]) => (
                        <div key={id}>
                            <p>This requirement changed and can&apos;t be submitted right now. Your note is kept below.</p>
                            <textarea readOnly value={text} aria-label="Kept note" />
                            <button type="button" onClick={() => dropNote(Number(id))}>Dismiss</button>
                        </div>
                    ))}
                </section>
            )}
        </div>
    )
}

export default StudentRequirementsPage
