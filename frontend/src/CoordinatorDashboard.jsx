import { useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import { roleText } from './format.js'
import ProgressCard from './ProgressCard.jsx'
import { statusLabel } from './requirements.js'
import StatusBadge from './StatusBadge.jsx'

/**
 * Coordinator Dashboard tab (MVP §4.6). The server computes the totals and the
 * "needs attention" reasons (AssistanceRules); this page only displays them.
 * Refreshes whenever the tab becomes active.
 */
function CoordinatorDashboard({ active, goTo }) {
    const [data, setData] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        apiFetch('/api/coordinator/dashboard')
            .then((result) => {
                if (cancelled) return
                setData(result)
                setError(null)
            })
            .catch((err) => !cancelled && setError(err.message))
        return () => { cancelled = true }
    }, [active])

    if (error) return <p role="alert">Could not load the dashboard: {error}</p>
    if (!data) return <p className="loading" role="status">Loading…</p>

    const { totals, students } = data
    const flagged = students.filter((s) => s.flags.length > 0)

    return (
        <div>
            <h1>Dashboard</h1>

            {/* The totals are context, so they are one ruled strip; "Needs attention" is the priority. */}
            <ul className="totals" aria-label="Totals">
                <li><span className="figure">{totals.students}</span> students</li>
                <li><span className="figure">{totals.activePlacements}</span> active placements</li>
                <li><span className="figure">{totals.completedPlacements}</span> completed</li>
                <li>
                    <span className="figure">{totals.logsAwaitingReview}</span> logs awaiting review
                    {totals.logsAwaitingReview > 0 && (
                        <><br /><button type="button" onClick={() => goTo('review')}>Open review queue</button></>
                    )}
                </li>
                <li>
                    <span className="figure">{totals.requirementsAwaitingReview}</span> requirement submissions
                    awaiting review
                    {totals.requirementsAwaitingReview > 0 && (
                        <><br /><button type="button" onClick={() => goTo('requirements')}>Open Requirements</button></>
                    )}
                </li>
            </ul>

            <section aria-label="Needs attention">
                <h2>Needs attention</h2>
                {flagged.length === 0 ? <p className="empty">No students need attention right now.</p> : (
                    <ul className="attention">
                        {flagged.map((s) => (
                            <li key={s.studentId}>
                                <div>
                                    <strong>{s.fullName}</strong>
                                    {s.placement && <p className="muted">{s.placement.companyName}</p>}
                                </div>
                                <ul className="attention-flags">
                                    {s.flags.map((flag) => (
                                        <li key={flag.code}>
                                            <span className="flag">
                                                <span className="flag-mark" aria-hidden="true">!</span>
                                                {flag.message}
                                            </span>
                                            {flag.code === 'OVERDUE_REQUIREMENTS' && (
                                                <ul>
                                                    {s.overdueRequirements.map((r) => (
                                                        <li key={r.requirementId}>
                                                            {r.title}, due <time>{r.dueDate}</time>{' '}
                                                            ({statusLabel(r.status)})
                                                        </li>
                                                    ))}
                                                </ul>
                                            )}
                                        </li>
                                    ))}
                                </ul>
                            </li>
                        ))}
                    </ul>
                )}
                <p className="muted">
                    These are prompts to check in, not conclusions: for example, &quot;No logs since …&quot; can be a
                    holiday. &quot;No placement yet&quot; only appears from the placement search start date you set
                    in Students.
                </p>
            </section>

            <section aria-label="All students">
                <h2>All students</h2>
                {students.length === 0 ? <p className="empty">No students yet. Create them in Students.</p> : (
                    // stack-table: a card per student on phones; .cell-label is shown only there
                    <table className="stack-table">
                        <thead>
                            <tr>
                                <th>Student</th>
                                <th>Placement</th>
                                <th>Progress</th>
                                <th>Search starts on</th>
                            </tr>
                        </thead>
                        <tbody>
                            {students.map((s) => (
                                <tr key={s.studentId}>
                                    <td>{s.fullName}</td>
                                    <td>
                                        <span className="cell-label">Placement</span>
                                        {s.placement ? (
                                            <>
                                                {s.placement.companyName} · {roleText(s.placement.role)}{' '}
                                                <StatusBadge kind="placement" status={s.placement.status} />
                                            </>
                                        ) : '—'}
                                    </td>
                                    <td>
                                        <span className="cell-label">Progress</span>
                                        {s.placement ? (
                                            <>
                                                <ProgressCard summary={s.placement.progress} compact />
                                                {s.placement.status === 'ACTIVE' && (
                                                    <button type="button" onClick={() => goTo('progress')}>Progress</button>
                                                )}
                                            </>
                                        ) : '—'}
                                    </td>
                                    <td>
                                        <span className="cell-label">Search starts on</span>
                                        {s.placementSearchStartsOn ?? 'not set'}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </section>
        </div>
    )
}

export default CoordinatorDashboard
