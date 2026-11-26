import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import RequirementDetail from './RequirementDetail.jsx'
import RequirementForm from './RequirementForm.jsx'
import { createBody, draftProblem, emptyDraft, progressText } from './requirements.js'

/**
 * Coordinator Requirements tab (MVP §4.7): the requirements I own, a New requirement form,
 * and one opened requirement (RequirementDetail) where submissions are reviewed.
 *
 * Every DRAFT lives here, not in the form or the detail: those are unmounted when closed,
 * and React drops a component's state when it unmounts. This page itself stays mounted
 * (Tabs.jsx only hides inactive tabs), so drafts survive closing the detail and switching
 * tabs. They are cleared on logout or when another user logs in (App.jsx key={user.id}).
 */
function CoordinatorRequirementsPage({ active }) {
    const [requirements, setRequirements] = useState([])
    const [students, setStudents] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [token, setToken] = useState(0)                 // +1 reloads the list AND the open detail
    const [showArchived, setShowArchived] = useState(false)
    const [openId, setOpenId] = useState(null)
    const [message, setMessage] = useState(null)          // { kind: 'status' | 'alert', text }

    // ---- drafts (kept until saved or discarded) ----
    const [createDraft, setCreateDraft] = useState(null)  // null = form closed; see requirements.js emptyDraft
    const [createError, setCreateError] = useState(null)
    const [creating, setCreating] = useState(false)
    const [editDraft, setEditDraft] = useState(null)      // { requirementId, title, description, dueDate, understood }
    const [reviewDrafts, setReviewDrafts] = useState({})  // `${requirementId}:${assignmentId}:${action}` -> text

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        Promise.all([
            apiFetch(`/api/coordinator/requirements?includeArchived=${showArchived}`),
            apiFetch('/api/coordinator/students'),
        ])
            .then(([list, mine]) => {
                if (cancelled) return
                setRequirements(list)
                setStudents(mine)
                setLoadError(null)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [active, token, showArchived])

    const reload = () => setToken((t) => t + 1)

    function setReviewDraft(key, text) {
        setReviewDrafts((prev) => ({ ...prev, [key]: text }))
    }

    function dropReviewDraft(key) {
        setReviewDrafts((prev) => {
            const next = { ...prev }
            delete next[key]
            return next
        })
    }

    async function create() {
        // A replacement's old requirement is ALREADY archived: say so, so a failure here isn't misread.
        const explain = (reason) => (createDraft.replacing
            ? `"${createDraft.replacing}" was archived, but the replacement wasn't created: ${reason} `
                + 'Your draft is kept. Fix it and save again.'
            : reason)
        const problem = draftProblem(createDraft)
        if (problem) {
            setCreateError(explain(problem))
            return
        }
        setCreating(true)
        setCreateError(null)
        try {
            const created = await apiJson('/api/coordinator/requirements', 'POST', createBody(createDraft))
            setCreateDraft(null)
            setMessage({ kind: 'status',
                text: `Created "${created.title}" for ${created.progressCounts.notStarted} student(s).` })
            reload()
        } catch (err) {
            setCreateError(explain(err.message))   // the draft stays exactly as typed
        } finally {
            setCreating(false)
        }
    }

    function discardCreate() {
        if (!window.confirm('Discard this draft?')) return
        setCreateDraft(null)
        setCreateError(null)
    }

    // The detail archived or deleted its requirement: close it, report, reload the list.
    function closeAfter(text) {
        setOpenId(null)
        setMessage({ kind: 'status', text })
        reload()
    }

    function startReplacement(draft) {
        setOpenId(null)
        setCreateDraft(draft)
        setCreateError(null)
        setMessage({ kind: 'status', text: `Archived "${draft.replacing}". Review the replacement below and create it.` })
        reload()
    }

    if (loading) return <p>Loading...</p>
    if (loadError) return <p role="alert">Could not load requirements: {loadError}</p>

    const opened = requirements.find((r) => r.id === openId)

    return (
        <div>
            <h1>Requirements</h1>
            {message && <p role={message.kind}>{message.text}</p>}

            {createDraft ? (
                <RequirementForm mode="create" draft={createDraft} onChange={setCreateDraft} onSave={create}
                                 onCancel={discardCreate} students={students} busy={creating} error={createError} />
            ) : (
                <button type="button" onClick={() => setCreateDraft(emptyDraft())}>New requirement</button>
            )}

            <h2>My requirements</h2>
            <label>
                <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
                {' '}Show archived
            </label>
            {requirements.length === 0 ? <p>No requirements yet.</p> : (
                <table>
                    <thead>
                        <tr>
                            <th>Requirement</th>
                            <th>Due</th>
                            <th>Progress</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>
                        {requirements.map((r) => (
                            <tr key={r.id}>
                                <td>{r.title}{r.archived && ' (Archived)'}</td>
                                <td>{r.dueDate ?? '—'}</td>
                                <td>{progressText(r.progressCounts, r.archived)}</td>
                                <td>
                                    <button type="button" aria-pressed={r.id === openId}
                                            onClick={() => setOpenId(r.id === openId ? null : r.id)}>
                                        {r.id === openId ? 'Close' : 'Open'}
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}

            {opened && (
                // key: opening another requirement starts its detail fresh (drafts live here, so none are lost)
                <RequirementDetail key={opened.id} requirement={opened} students={students} token={token}
                                   onChanged={reload} onClose={() => setOpenId(null)}
                                   onArchived={closeAfter} onReplace={startReplacement}
                                   drafts={reviewDrafts} setDraft={setReviewDraft} dropDraft={dropReviewDraft}
                                   editDraft={editDraft} setEditDraft={setEditDraft} />
            )}
        </div>
    )
}

export default CoordinatorRequirementsPage
