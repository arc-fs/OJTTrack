import { useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import { formatMinutes } from './format.js'

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
    if (!data) return <p>Loading...</p>

    const { totals, students } = data
    const flagged = students.filter((s) => s.flags.length > 0)

    return (
        <div>
            <h1>Dashboard</h1>

            <section aria-label="Totals">
                <p>
                    {totals.students} student(s) · {totals.activePlacements} active placement(s) ·{' '}
                    {totals.completedPlacements} completed
                </p>
                <p>
                    {totals.logsAwaitingReview} log(s) awaiting review{' '}
                    {totals.logsAwaitingReview > 0 && (
                        <button type="button" onClick={() => goTo('review')}>Open review queue</button>
                    )}
                </p>
            </section>

            <section aria-label="Needs attention">
                <h2>Needs attention</h2>
                {flagged.length === 0 ? <p>No students need attention right now.</p> : (
                    <ul>
                        {flagged.map((s) => (
                            <li key={s.studentId}>
                                <strong>{s.fullName}</strong>
                                {s.placement && <> · {s.placement.companyName}</>}
                                <ul>
                                    {s.flags.map((flag) => <li key={flag.code}>{flag.message}</li>)}
                                </ul>
                            </li>
                        ))}
                    </ul>
                )}
                <p>
                    These are prompts to check in, not conclusions: for example, &quot;No logs since …&quot; can be a
                    holiday. &quot;No placement yet&quot; only appears from the placement search start date you set
                    in Students.
                </p>
            </section>

            <section aria-label="All students">
                <h2>All students</h2>
                {students.length === 0 ? <p>No students yet. Create them in Students.</p> : (
                    <table>
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
                                    <td>{s.placement ? `${s.placement.companyName} (${s.placement.status})` : '—'}</td>
                                    <td>
                                        {s.placement ? (
                                            <>
                                                {formatMinutes(s.placement.progress.verifiedMinutes)} of{' '}
                                                {formatMinutes(s.placement.progress.requiredMinutes)} verified
                                                {s.placement.status === 'ACTIVE' && (
                                                    <> <button type="button" onClick={() => goTo('progress')}>Progress</button></>
                                                )}
                                            </>
                                        ) : '—'}
                                    </td>
                                    <td>{s.placementSearchStartsOn ?? 'not set'}</td>
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
