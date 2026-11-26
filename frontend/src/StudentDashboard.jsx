import { useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import ProgressCard from './ProgressCard.jsx'
import { statusLabel } from './requirements.js'

const STATUS_ORDER = ['PLANNED', 'APPLIED', 'INTERVIEW', 'OFFERED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN']

/**
 * Student Dashboard tab (MVP §4.5). Shows what the server summarised in GET /api/dashboard;
 * no rules are calculated here. Refreshes whenever the tab becomes active.
 */
function StudentDashboard({ active, goTo }) {
    const [data, setData] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        apiFetch('/api/dashboard')
            .then((result) => {
                if (cancelled) return
                setData(result)
                setError(null)
            })
            .catch((err) => !cancelled && setError(err.message))
        return () => { cancelled = true }
    }, [active])

    if (error) return <p role="alert">Could not load your dashboard: {error}</p>
    if (!data) return <p>Loading...</p>

    const overdue = data.followUps.filter((f) => f.overdue)
    const upcoming = data.followUps.filter((f) => !f.overdue)
    const progress = data.placement?.progress

    return (
        <div>
            <h1>Dashboard</h1>

            <section aria-label="Placement">
                <h2>My placement</h2>
                {!data.placement ? (
                    <p>No placement yet. <button type="button" onClick={() => goTo('applications')}>Go to Applications &amp; placement</button></p>
                ) : (
                    <>
                        <p><strong>{data.placement.companyName}</strong> ({data.placement.status}) ·{' '}
                            {data.placement.startDate} to {data.placement.endDate}</p>
                        <ProgressCard summary={progress}
                                      completionNote="Waiting for your coordinator to confirm completion." />
                    </>
                )}
                {data.pendingPlacementRequest && (
                    <p role="status">
                        {data.pendingPlacementRequest.type === 'CHANGE' ? 'Change to ' : 'Placement at '}
                        <strong>{data.pendingPlacementRequest.companyName}</strong> is waiting for your coordinator.
                    </p>
                )}
            </section>

            {progress && (progress.pendingCount > 0 || progress.returnedCount > 0) && (
                <section aria-label="Time logs">
                    <h2>Time logs</h2>
                    {progress.returnedCount > 0 && (
                        <p role="alert">
                            {progress.returnedCount} returned log(s) need your correction.{' '}
                            <button type="button" onClick={() => goTo('time-logs')}>Fix them in Time logs</button>
                        </p>
                    )}
                    {progress.pendingCount > 0 && <p>{progress.pendingCount} log(s) waiting for review.</p>}
                </section>
            )}

            <RequirementsSection requirements={data.requirements} goTo={goTo} />

            <section aria-label="Follow-ups">
                <h2>Follow-ups</h2>
                {data.followUps.length === 0 ? <p>No follow-ups due in the next 7 days.</p> : (
                    <>
                        {overdue.length > 0 && (
                            <>
                                <h3>Overdue</h3>
                                <ul>{overdue.map((f) => <li key={f.applicationId}>{f.companyName} ({f.status}): {f.followUpDate}</li>)}</ul>
                            </>
                        )}
                        {upcoming.length > 0 && (
                            <>
                                <h3>Due within 7 days</h3>
                                <ul>{upcoming.map((f) => <li key={f.applicationId}>{f.companyName} ({f.status}): {f.followUpDate}</li>)}</ul>
                            </>
                        )}
                    </>
                )}
            </section>

            <section aria-label="Applications">
                <h2>Applications</h2>
                <ul>
                    {STATUS_ORDER.map((status) => (
                        <li key={status}>{status}: {data.applicationCounts[status] ?? 0}</li>
                    ))}
                </ul>
                <button type="button" onClick={() => goTo('applications')}>Manage applications</button>
            </section>
        </div>
    )
}

/** Overdue, due within 7 days, and returned requirements, as the server listed them. */
function RequirementsSection({ requirements, goTo }) {
    const { overdue, dueSoon, returned } = requirements
    const item = (r) => (
        <li key={r.assignmentId}>
            {r.title}{r.dueDate && <>: due {r.dueDate}</>} ({statusLabel(r.status)})
        </li>
    )

    return (
        <section aria-label="Requirements">
            <h2>Requirements</h2>
            {overdue.length + dueSoon.length + returned.length === 0 ? (
                <p>Nothing overdue or due in the next 7 days.</p>
            ) : (
                <>
                    {overdue.length > 0 && <><h3>Overdue</h3><ul>{overdue.map(item)}</ul></>}
                    {dueSoon.length > 0 && <><h3>Due within 7 days</h3><ul>{dueSoon.map(item)}</ul></>}
                    {returned.length > 0 && (
                        <>
                            <h3>Returned for changes</h3>
                            <ul>
                                {returned.map((r) => (
                                    <li key={r.assignmentId}>{r.title}: <em>{r.feedback}</em></li>
                                ))}
                            </ul>
                        </>
                    )}
                </>
            )}
            <button type="button" onClick={() => goTo('requirements')}>Open Requirements</button>
        </section>
    )
}

export default StudentDashboard
