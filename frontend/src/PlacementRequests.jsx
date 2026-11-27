import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'

// Mirrors ApprovePlacementRequest / RejectPlacementRequest on the backend.
// The server re-checks everything; these only give faster feedback.
const MAX_HOURS = 5000
const MAX_NOTE = 500
const MAX_ROLE = 150   // placements.role VARCHAR(150)

function validateApproval({ role, startDate, endDate, requiredHours }) {
    if (!role.trim()) return "Please enter the placement's role (job title)."
    if (!startDate || !endDate) return 'Please choose both a start and an end date.'
    if (endDate < startDate) return 'The end date must be on or after the start date.'   // ISO dates compare as strings
    const hours = Number(requiredHours)
    if (!Number.isInteger(hours) || hours < 1 || hours > MAX_HOURS) {
        return `Required hours must be a whole number from 1 to ${MAX_HOURS}.`
    }
    return null
}

/** Pending placement requests from this coordinator's students, with approve/reject. */
function PlacementRequests() {
    const [requests, setRequests] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [success, setSuccess] = useState(null)

    // Which request has its form open, and whether it is approving or rejecting.
    const [openId, setOpenId] = useState(null)
    const [mode, setMode] = useState(null)
    const [form, setForm] = useState({ role: '', startDate: '', endDate: '', requiredHours: '', note: '' })
    const [formError, setFormError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        apiFetch('/api/coordinator/placement-requests')
            .then((data) => setRequests(data))
            .catch((err) => setLoadError(err.message))
            .finally(() => setLoading(false))
    }, [])

    function open(id, newMode) {
        setOpenId(id)
        setMode(newMode)
        setForm({ role: '', startDate: '', endDate: '', requiredHours: '', note: '' })
        setFormError(null)
        setSuccess(null)
    }

    function close() {
        setOpenId(null)
        setMode(null)
    }

    function update(field, value) {
        setForm((prev) => ({ ...prev, [field]: value }))
    }

    async function handleDecide(event, request) {
        event.preventDefault()
        setFormError(null)
        const note = form.note.trim() || null

        let url
        let body
        if (mode === 'approve') {
            const problem = validateApproval(form)
            if (problem) {
                setFormError(problem)
                return
            }
            url = `/api/coordinator/placement-requests/${request.id}/approve`
            body = {
                role: form.role.trim(),
                startDate: form.startDate,
                endDate: form.endDate,
                requiredHours: Number(form.requiredHours),
                note,
            }
        } else {
            url = `/api/coordinator/placement-requests/${request.id}/reject`
            body = { note }
        }

        setSubmitting(true)
        try {
            await apiJson(url, 'POST', body)
            setRequests((prev) => prev.filter((r) => r.id !== request.id))
            setSuccess(`${mode === 'approve' ? 'Approved' : 'Rejected'} ${request.studentName}’s request for ${request.companyName}.`)
            close()
        } catch (err) {
            setFormError('Could not save: ' + err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <section>
            <h2>Placement requests</h2>
            {success && <p role="status">{success}</p>}
            {loading ? (
                <p className="loading" role="status">Loading…</p>
            ) : loadError ? (
                <p role="alert">Could not load placement requests: {loadError}</p>
            ) : requests.length === 0 ? (
                <p className="empty">No pending placement requests.</p>
            ) : (
                // card-list / card-title / card-actions only change the phone layout
                <ul className="card-list">
                    {requests.map((r) => (
                        <li key={r.id}>
                            <strong className="card-title">{r.studentName}</strong>:{' '}
                            {r.type === 'CHANGE' ? 'change to' : 'placement at'}{' '}
                            <strong>{r.companyName}</strong>, requested {new Date(r.createdAt).toLocaleDateString()}
                            {r.reason && <> · “{r.reason}”</>}{' '}
                            {openId !== r.id && (
                                <span className="card-actions">
                                    <button onClick={() => open(r.id, 'approve')}>Approve</button>{' '}
                                    <button onClick={() => open(r.id, 'reject')}>Reject</button>
                                </span>
                            )}

                            {openId === r.id && (
                                <form onSubmit={(e) => handleDecide(e, r)}>
                                    {mode === 'approve' && (
                                        <>
                                            <label htmlFor={`role-${r.id}`}>Role (job title)</label>
                                            <input id={`role-${r.id}`} required maxLength={MAX_ROLE} value={form.role}
                                                   onChange={(e) => update('role', e.target.value)} />
                                            <label htmlFor={`start-${r.id}`}>Start date</label>
                                            <input id={`start-${r.id}`} type="date" value={form.startDate}
                                                   onChange={(e) => update('startDate', e.target.value)} />
                                            <label htmlFor={`end-${r.id}`}>End date</label>
                                            <input id={`end-${r.id}`} type="date" value={form.endDate}
                                                   min={form.startDate || undefined}
                                                   onChange={(e) => update('endDate', e.target.value)} />
                                            <label htmlFor={`hours-${r.id}`}>Required hours</label>
                                            <input id={`hours-${r.id}`} type="number" min="1" max={MAX_HOURS} step="1"
                                                   value={form.requiredHours}
                                                   onChange={(e) => update('requiredHours', e.target.value)} />
                                        </>
                                    )}
                                    <label htmlFor={`note-${r.id}`}>Note (optional)</label>
                                    <input id={`note-${r.id}`} maxLength={MAX_NOTE} value={form.note}
                                           onChange={(e) => update('note', e.target.value)} />
                                    {mode === 'approve' && r.type === 'CHANGE' && (
                                        <p>Approving ends the student’s current placement.</p>
                                    )}
                                    {/* Primary only for approval: the same button confirms a rejection */}
                                    <button type="submit" disabled={submitting}
                                            data-variant={mode === 'approve' ? 'primary' : undefined}>
                                        {submitting ? 'Saving...' : mode === 'approve' ? 'Confirm approval' : 'Confirm rejection'}
                                    </button>{' '}
                                    <button type="button" data-variant="quiet" onClick={close} disabled={submitting}>Cancel</button>
                                    {formError && <p role="alert">{formError}</p>}
                                </form>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    )
}

export default PlacementRequests
