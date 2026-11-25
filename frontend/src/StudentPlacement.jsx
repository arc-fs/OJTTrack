import { useState } from 'react'
import { apiJson } from './api.js'

const MAX_REASON = 500   // placement_requests.reason VARCHAR(500)

function formatInstant(instant) {
    return instant ? new Date(instant).toLocaleDateString() : ''
}

/**
 * The student's placement: current one, pending request, a form to request one,
 * and history. It owns no data: ApplicationsPage passes everything in and is told
 * about new requests through onSubmitted ("lifting state up").
 */
function StudentPlacement({ applications, placements, requests, onSubmitted }) {
    const active = placements.find((p) => p.status === 'ACTIVE')
    const pending = requests.find((r) => r.status === 'PENDING')
    const ended = placements.filter((p) => p.status !== 'ACTIVE')
    const rejected = requests.filter((r) => r.status === 'REJECTED')

    // An application can become a placement only once, and only if ACCEPTED.
    const usedIds = new Set(placements.map((p) => p.applicationId))
    const usable = applications.filter((a) => a.status === 'ACCEPTED' && !usedIds.has(a.id))

    const [applicationId, setApplicationId] = useState('')
    const [reason, setReason] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        if (!applicationId) {
            setError('Please choose an accepted application.')
            return
        }
        setSubmitting(true)
        try {
            const created = await apiJson('/api/placement-requests', 'POST', {
                applicationId: Number(applicationId),
                reason: reason.trim() || null,
            })
            onSubmitted(created)
            setApplicationId('')
            setReason('')
        } catch (err) {
            setError('Could not send request: ' + err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <section>
            <h2>My placement</h2>

            {active ? (
                <div>
                    <p><strong>{active.companyName}</strong> (ACTIVE)</p>
                    <p>{active.startDate} to {active.endDate} · {active.requiredHours} required hours</p>
                </div>
            ) : (
                <p>No placement yet.</p>
            )}

            {pending ? (
                <p role="status">
                    {pending.type === 'CHANGE' ? 'Change to ' : 'Placement at '}
                    <strong>{pending.companyName}</strong> requested on {formatInstant(pending.createdAt)}.
                    Waiting for your coordinator.
                </p>
            ) : usable.length === 0 ? (
                <p>You need an ACCEPTED application that hasn’t been used before to request a placement.</p>
            ) : (
                <form onSubmit={handleSubmit}>
                    <label htmlFor="placement-application">Accepted application</label>
                    <select
                        id="placement-application"
                        value={applicationId}
                        onChange={(e) => setApplicationId(e.target.value)}
                    >
                        <option value="">Choose…</option>
                        {usable.map((a) => (
                            <option key={a.id} value={a.id}>{a.companyName}</option>
                        ))}
                    </select>
                    <label htmlFor="placement-reason">Reason (optional)</label>
                    <input
                        id="placement-reason"
                        maxLength={MAX_REASON}
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                    />
                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Sending...' : active ? 'Request change' : 'Request placement'}
                    </button>
                    {active && <p>Your current placement stays active until your coordinator approves the change.</p>}
                    {error && <p role="alert">{error}</p>}
                </form>
            )}

            {(ended.length > 0 || rejected.length > 0) && (
                <details>
                    <summary>History</summary>
                    <ul>
                        {ended.map((p) => (
                            <li key={'p' + p.id}>
                                {p.companyName}: {p.status} on {formatInstant(p.endedAt)}
                            </li>
                        ))}
                        {rejected.map((r) => (
                            <li key={'r' + r.id}>
                                Request for {r.companyName} rejected on {formatInstant(r.decidedAt)}
                                {r.decisionNote && <>: “{r.decisionNote}”</>}
                            </li>
                        ))}
                    </ul>
                </details>
            )}
        </section>
    )
}

export default StudentPlacement
