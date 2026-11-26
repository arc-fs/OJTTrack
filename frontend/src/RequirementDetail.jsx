import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import RequirementForm from './RequirementForm.jsx'
import RequirementHistory from './RequirementHistory.jsx'
import {
    MAX_REASON,
    anySubmitted,
    editWarning,
    replacementDraft,
    statusLabel,
    unassignedStudents,
} from './requirements.js'

// What each review action needs: its URL segment, the request field and the button text.
const ACTIONS = {
    return: { field: 'feedback', label: 'Return with feedback', prompt: 'Feedback for the student', done: 'Returned' },
    unconfirm: { field: 'reason', label: 'Un-confirm', prompt: 'Reason (the student sees it)', done: 'Un-confirmed' },
}

/** Which review actions this row allows right now (the server checks the same rules). */
function allowed(assignment, archived) {
    if (archived) return []
    if (assignment.status === 'SUBMITTED') return ['confirm', 'return']
    if (assignment.status === 'CONFIRMED') return ['unconfirm']
    return []
}

/**
 * One requirement: every assigned student's status with Confirm / Return / Un-confirm, plus
 * edit, archive, delete and assign. It is unmounted when closed, so every DRAFT (review
 * texts, the edit form) is owned by CoordinatorRequirementsPage and passed in here.
 * `token` changes whenever the page reloads, which reloads this table too.
 */
function RequirementDetail({ requirement, students, token, onChanged, onClose, onArchived, onReplace,
                             drafts, setDraft, dropDraft, editDraft, setEditDraft }) {
    const [assignments, setAssignments] = useState(null)
    const [loadError, setLoadError] = useState(null)
    const [openAction, setOpenAction] = useState(null)   // `${assignmentId}:return|unconfirm`
    const [toAssign, setToAssign] = useState([])
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState(null)         // { kind: 'status' | 'alert', text }

    const id = requirement.id
    const archived = requirement.archived

    useEffect(() => {
        let cancelled = false
        apiFetch(`/api/coordinator/requirements/${id}/assignments`)
            .then((result) => {
                if (cancelled) return
                setAssignments(result)
                setLoadError(null)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
        return () => { cancelled = true }
    }, [id, token])

    const draftKey = (assignmentId, action) => `${id}:${assignmentId}:${action}`

    // Runs one server call. Success: message + reload. Failure: the server's message; on 409 reload too.
    // Drafts are never touched here: callers drop a draft only after their own call succeeded.
    async function run(call, successText) {
        setBusy(true)
        setMessage(null)
        try {
            await call()
            setMessage({ kind: 'status', text: successText })
            onChanged()
            return true
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })
            if (err.status === 409) onChanged()
            return false
        } finally {
            setBusy(false)
        }
    }

    async function review(a, action) {
        const url = `/api/coordinator/requirement-assignments/${a.assignmentId}/${action}`
        if (action === 'confirm') {
            await run(() => apiJson(url, 'POST', { version: a.version }), `Confirmed ${a.studentName}.`)
            return
        }
        const key = draftKey(a.assignmentId, action)
        const text = (drafts[key] ?? '').trim()
        if (!text) {
            setMessage({ kind: 'alert', text: 'Write a reason so the student knows what to fix.' })
            return
        }
        const ok = await run(() => apiJson(url, 'POST', { version: a.version, [ACTIONS[action].field]: text }),
            `${ACTIONS[action].done} ${a.studentName}'s submission.`)
        if (ok) {
            dropDraft(key)
            setOpenAction(null)
        }
    }

    async function remove(a) {
        if (!window.confirm(`Remove ${a.studentName} from "${requirement.title}"?`)) return
        await run(() => apiFetch(`/api/coordinator/requirements/${id}/students/${a.studentId}`, { method: 'DELETE' }),
            `Removed ${a.studentName}.`)
    }

    async function assign() {
        const ok = await run(() => apiJson(`/api/coordinator/requirements/${id}/assign`, 'POST', { studentIds: toAssign }),
            `Assigned ${toAssign.length} student(s).`)
        if (ok) setToAssign([])
    }

    function startEdit() {
        // One edit draft at a time: don't silently drop unsaved changes to another requirement.
        if (editDraft && !window.confirm('Discard your unsaved edit of another requirement?')) return
        setEditDraft({ requirementId: id, title: requirement.title, description: requirement.description ?? '',
            dueDate: requirement.dueDate ?? '', understood: false })
    }

    async function saveEdit() {
        if (!editDraft.title.trim()) {
            setMessage({ kind: 'alert', text: 'Enter a title.' })
            return
        }
        const ok = await run(() => apiJson(`/api/coordinator/requirements/${id}`, 'PUT', {
            version: requirement.version,
            title: editDraft.title.trim(),
            description: editDraft.description.trim() || null,
            dueDate: editDraft.dueDate || null,
        }), 'Saved.')
        if (ok) setEditDraft(null)
    }

    function cancelEdit() {
        if (window.confirm('Discard your changes to this requirement?')) setEditDraft(null)
    }

    async function archive() {
        if (!window.confirm(`Archive "${requirement.title}"? Students will no longer see it. History is kept.`)) return
        setBusy(true)
        try {
            await apiJson(`/api/coordinator/requirements/${id}/archive`, 'POST', { version: requirement.version })
            onArchived(`Archived "${requirement.title}".`)
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })
            if (err.status === 409) onChanged()
        } finally {
            setBusy(false)
        }
    }

    // Step 1: archive (with the version). Step 2, only if that worked: hand the page a prefilled draft.
    async function archiveAndReplace() {
        if (!window.confirm(`Archive "${requirement.title}" and start a replacement?`)) return
        setBusy(true)
        try {
            await apiJson(`/api/coordinator/requirements/${id}/archive`, 'POST', { version: requirement.version })
            setEditDraft(null)
            onReplace(replacementDraft(requirement, assignments ?? [], students))
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })
            if (err.status === 409) onChanged()
        } finally {
            setBusy(false)
        }
    }

    async function deleteRequirement() {
        if (!window.confirm(`Delete "${requirement.title}"? This can't be undone.`)) return
        setBusy(true)
        try {
            await apiFetch(`/api/coordinator/requirements/${id}?version=${requirement.version}`, { method: 'DELETE' })
            onArchived(`Deleted "${requirement.title}".`)
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })   // e.g. 409 "…Archive it instead"
            if (err.status === 409) onChanged()
        } finally {
            setBusy(false)
        }
    }

    if (loadError) return <p role="alert">Could not load this requirement: {loadError}</p>
    if (!assignments) return <p>Loading...</p>

    const choices = archived ? [] : unassignedStudents(students, assignments)
    const editing = editDraft?.requirementId === id

    // Drafts for this requirement that no row can use any more (it changed, or was archived).
    const usable = new Set(assignments.flatMap((a) => allowed(a, archived).map((action) => draftKey(a.assignmentId, action))))
    const keptDrafts = Object.entries(drafts)
        .filter(([key, text]) => key.startsWith(`${id}:`) && !usable.has(key) && text.trim())

    return (
        <section aria-label={`Requirement ${requirement.title}`}>
            <h2>
                {requirement.title}{archived && ' (Archived, read-only)'}
            </h2>
            {requirement.description && <p>{requirement.description}</p>}
            <p>{requirement.dueDate ? <>Due {requirement.dueDate}</> : 'No due date'}</p>
            {message && <p role={message.kind}>{message.text}</p>}

            {!archived && !editing && (
                <>
                    <button type="button" disabled={busy} onClick={startEdit}>Edit</button>{' '}
                    <button type="button" disabled={busy} onClick={archive}>Archive</button>{' '}
                </>
            )}
            {!anySubmitted(requirement.progressCounts) && (
                <><button type="button" data-variant="danger" disabled={busy} onClick={deleteRequirement}>
                    Delete
                </button>{' '}</>
            )}
            <button type="button" onClick={onClose}>Close</button>

            {editing && !archived && (
                <RequirementForm mode="edit" draft={editDraft} onChange={setEditDraft} onSave={saveEdit}
                                 onCancel={cancelEdit} busy={busy} warning={editWarning(requirement.progressCounts)}
                                 onArchiveAndReplace={editWarning(requirement.progressCounts) ? archiveAndReplace : null} />
            )}

            <h3>Students</h3>
            {assignments.length === 0 ? <p>No students are assigned.</p> : (
                // stack-table: a card per student on phones; .cell-label is shown only there
                <table className="stack-table">
                    <thead>
                        <tr>
                            <th>Student</th>
                            <th>Status</th>
                            <th>Submission</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {assignments.map((a) => {
                            const actions = allowed(a, archived)
                            return (
                                <tr key={a.assignmentId}>
                                    <td>{a.studentName}</td>
                                    <td>
                                        <span className="cell-label">Status</span>
                                        {statusLabel(a.status, archived)}
                                        {a.overdue && <> <strong className="badge">[Overdue]</strong></>}
                                        {a.confirmedByName && <> by {a.confirmedByName}</>}
                                    </td>
                                    <td>
                                        <span className="cell-label">Submission</span>
                                        {a.submittedAt ? <>{new Date(a.submittedAt).toLocaleString()}</> : 'Not submitted'}
                                        {a.note && <p>{a.note}</p>}
                                        {a.feedback && <p><em>Feedback: {a.feedback}</em></p>}
                                        <RequirementHistory history={a.history} />
                                    </td>
                                    <td className="actions">
                                        {actions.includes('confirm') && (
                                            <><button type="button" data-variant="primary" disabled={busy}
                                                      onClick={() => review(a, 'confirm')}>
                                                Confirm
                                            </button>{' '}</>
                                        )}
                                        {actions.filter((action) => action !== 'confirm').map((action) => {
                                            const key = draftKey(a.assignmentId, action)
                                            const open = openAction === `${a.assignmentId}:${action}`
                                            return open ? (
                                                <div key={action}>
                                                    <label htmlFor={`req-${key}`}>{ACTIONS[action].prompt}</label>
                                                    <textarea id={`req-${key}`} maxLength={MAX_REASON} value={drafts[key] ?? ''}
                                                              onChange={(e) => setDraft(key, e.target.value)} />
                                                    <button type="button" disabled={busy || !(drafts[key] ?? '').trim()}
                                                            onClick={() => review(a, action)}>
                                                        {ACTIONS[action].label}
                                                    </button>{' '}
                                                    <button type="button" onClick={() => setOpenAction(null)}>Cancel</button>
                                                    <span> (your draft is kept)</span>
                                                </div>
                                            ) : (
                                                <button key={action} type="button" disabled={busy}
                                                        onClick={() => setOpenAction(`${a.assignmentId}:${action}`)}>
                                                    {action === 'return' ? 'Return…' : 'Un-confirm…'}
                                                </button>
                                            )
                                        })}
                                        {!archived && a.status === 'NOT_STARTED' && a.history.length === 0 && (
                                            <button type="button" data-variant="danger" disabled={busy}
                                                    onClick={() => remove(a)}>Remove</button>
                                        )}
                                    </td>
                                </tr>
                            )
                        })}
                    </tbody>
                </table>
            )}

            {keptDrafts.length > 0 && (
                <section aria-label="Kept drafts">
                    <h3>Kept drafts</h3>
                    {keptDrafts.map(([key, text]) => (
                        <div key={key}>
                            <p>This submission changed or can&apos;t be reviewed any more. Your draft is kept below.</p>
                            <textarea readOnly value={text} aria-label="Kept draft" />
                            <button type="button" onClick={() => dropDraft(key)}>Dismiss</button>
                        </div>
                    ))}
                </section>
            )}

            {choices.length > 0 && (
                <fieldset>
                    <legend>Assign more students</legend>
                    {choices.map((s) => (
                        <label key={s.id}>
                            <input type="checkbox" checked={toAssign.includes(s.id)}
                                   onChange={(e) => setToAssign((prev) => (e.target.checked
                                       ? [...prev, s.id] : prev.filter((x) => x !== s.id)))} />
                            {' '}{s.fullName}{' '}
                        </label>
                    ))}
                    <button type="button" disabled={busy || toAssign.length === 0} onClick={assign}>Assign</button>
                </fieldset>
            )}
        </section>
    )
}

export default RequirementDetail
