import { useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import { roleText } from './format.js'
import ProgressCard from './ProgressCard.jsx'
import StatusBadge from './StatusBadge.jsx'
import { sentenceCase } from './status.js'

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
    if (!data) return <p className="loading" role="status">Loading…</p>

    const overdue = data.followUps.filter((f) => f.overdue)
    const upcoming = data.followUps.filter((f) => !f.overdue)
    const progress = data.placement?.progress
    const followUp = (f) => (
        <li key={f.applicationId}>
            {f.companyName}, {sentenceCase(f.status)}: <time>{f.followUpDate}</time>
        </li>
    )

    return (
        <div>
            <h1>Dashboard</h1>

            <section aria-label="Placement">
                <h2>My placement</h2>
                {!data.placement ? (
                    <>
                        <p className="empty">No placement yet. Request one from an accepted application.</p>
                        <button type="button" onClick={() => goTo('applications')}>Go to Applications &amp; placement</button>
                    </>
                ) : (
                    <>
                        <div className="record-head">
                            <p className="record-title">
                                <strong>{data.placement.companyName}</strong> · {roleText(data.placement.role)}
                            </p>
                            <StatusBadge kind="placement" status={data.placement.status} />
                        </div>
                        <p className="muted">
                            <time>{data.placement.startDate}</time> to <time>{data.placement.endDate}</time>
                        </p>
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

            <div className="dashboard-columns">
                <RequirementsSection requirements={data.requirements} goTo={goTo} />

                <div>
                    <section aria-label="Follow-ups">
                        <h2>Follow-ups</h2>
                        {data.followUps.length === 0 ? <p className="empty">No follow-ups due in the next 7 days.</p> : (
                            <>
                                {overdue.length > 0 && (
                                    <>
                                        <h3>Overdue</h3>
                                        <ul>{overdue.map(followUp)}</ul>
                                    </>
                                )}
                                {upcoming.length > 0 && (
                                    <>
                                        <h3>Due within 7 days</h3>
                                        <ul>{upcoming.map(followUp)}</ul>
                                    </>
                                )}
                            </>
                        )}
                    </section>

                    <section aria-label="Applications">
                        <h2>Applications</h2>
                        <ul className="counts">
                            {STATUS_ORDER.map((status) => (
                                <li key={status}>
                                    <span>{sentenceCase(status)}</span>
                                    <span>{data.applicationCounts[status] ?? 0}</span>
                                </li>
                            ))}
                        </ul>
                        <button type="button" onClick={() => goTo('applications')}>Manage applications</button>
                    </section>
                </div>
            </div>
        </div>
    )
}

/** Overdue, due within 7 days, and returned requirements, as the server listed them. */
function RequirementsSection({ requirements, goTo }) {
    const { overdue, dueSoon, returned } = requirements
    const item = (r) => (
        <li key={r.assignmentId}>
            {r.title}{r.dueDate && <>, due <time>{r.dueDate}</time></>}{' '}
            <StatusBadge kind="requirement" status={r.status} />
        </li>
    )

    return (
        <section aria-label="Requirements">
            <h2>Requirements</h2>
            {overdue.length + dueSoon.length + returned.length === 0 ? (
                <p className="empty">Nothing overdue or due in the next 7 days.</p>
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
