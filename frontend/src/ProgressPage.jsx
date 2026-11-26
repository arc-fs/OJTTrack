import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import { formatMinutes } from './format.js'
import ProgressCard from './ProgressCard.jsx'
import TimeLogTable from './TimeLogTable.jsx'

const MAX_REASON = 500   // stored as time_logs.feedback VARCHAR(500)

/**
 * Coordinator "Progress" tab: active placements of your students with their progress.
 * Opening one shows its logs, where a mistaken verification can be undone, and the
 * placement can be confirmed as complete. Un-verify reasons are drafts kept separately
 * from the loaded data, like the review queue's feedback.
 */
function ProgressPage({ active }) {
    const [placements, setPlacements] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [listToken, setListToken] = useState(0)
    const [openId, setOpenId] = useState(null)
    const [details, setDetails] = useState(null)    // { logs, summary } of the open placement
    const [detailError, setDetailError] = useState(null)
    const [detailToken, setDetailToken] = useState(0)
    const [reasons, setReasons] = useState({})      // logId -> un-verify reason draft
    const [unverifyingId, setUnverifyingId] = useState(null)
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState(null)    // { kind: 'status' | 'alert', text }

    useEffect(() => {
        if (!active) return undefined
        let cancelled = false
        apiFetch('/api/coordinator/placements')
            .then((items) => {
                if (cancelled) return
                setPlacements(items)
                setLoadError(null)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [active, listToken])

    useEffect(() => {
        if (!active || openId === null) return undefined
        let cancelled = false
        Promise.all([
            apiFetch(`/api/coordinator/placements/${openId}/time-logs`),
            apiFetch(`/api/coordinator/placements/${openId}/summary`),
        ])
            .then(([logs, summary]) => {
                if (cancelled) return
                setDetails({ logs, summary })
                setDetailError(null)
            })
            .catch((err) => !cancelled && setDetailError(err.message))
        return () => { cancelled = true }
    }, [active, openId, detailToken])

    function reloadAll() {
        setListToken((t) => t + 1)
        setDetailToken((t) => t + 1)
    }

    function open(placementId) {
        setMessage(null)
        setDetails(null)
        setDetailError(null)
        setUnverifyingId(null)
        setOpenId(placementId)
        // Re-opening the same placement ("Reload") doesn't change openId, and React only
        // re-runs an effect when a dependency changes, so bump the token to force the fetch.
        setDetailToken((t) => t + 1)
    }

    async function unverify(log) {
        const reason = (reasons[log.id] ?? '').trim()
        if (!reason) {
            setMessage({ kind: 'alert', text: 'Give a reason so the student knows what to fix.' })
            return
        }
        setBusy(true)
        setMessage(null)
        try {
            await apiJson(`/api/coordinator/time-logs/${log.id}/unverify`, 'POST', { version: log.version, reason })
            setReasons((prev) => {
                const next = { ...prev }
                delete next[log.id]
                return next
            })
            setUnverifyingId(null)
            setMessage({ kind: 'status', text: `Returned the log for ${log.logDate} to the student with your reason.` })
            reloadAll()
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })   // reason draft is kept
            if (err.status === 409) reloadAll()
        } finally {
            setBusy(false)
        }
    }

    async function complete(placement) {
        if (!window.confirm(`Confirm completion for ${placement.studentName} at ${placement.companyName}? `
            + 'This can’t be undone in the app.')) return
        setBusy(true)
        setMessage(null)
        try {
            await apiJson(`/api/coordinator/placements/${placement.placementId}/complete`, 'POST', {})
            setOpenId(null)
            setDetails(null)
            setMessage({ kind: 'status',
                text: `Completed ${placement.studentName}'s placement. It no longer appears under active placements.` })
            setListToken((t) => t + 1)
        } catch (err) {
            setMessage({ kind: 'alert', text: err.message })   // e.g. logs still pending
            if (err.status === 409) reloadAll()
        } finally {
            setBusy(false)
        }
    }

    if (loading) return <p>Loading...</p>
    if (loadError) return <p role="alert">Could not load placements: {loadError}</p>

    const opened = placements.find((p) => p.placementId === openId)
    const summary = details?.summary
    const canComplete = summary && summary.requirementReached && summary.pendingCount === 0
    const completeHint = !summary ? ''
        : summary.pendingCount > 0 ? `${summary.pendingCount} log(s) still pending: verify or return them first`
            : !summary.requirementReached ? `${formatMinutes(summary.remainingMinutes)} of verified hours still needed`
                : ''

    return (
        <div>
            <h1>Progress</h1>
            {message && <p role={message.kind}>{message.text}</p>}

            {placements.length === 0 ? <p>No active placements.</p> : (
                <ul>
                    {placements.map((p) => (
                        <li key={p.placementId}>
                            <strong>{p.studentName}</strong> · {p.companyName} · {p.requiredHours} h required{' '}
                            <button type="button" disabled={busy} onClick={() => open(p.placementId)}>
                                {p.placementId === openId ? 'Reload' : 'Open'}
                            </button>
                            <ProgressCard summary={p.progress} completionNote="Ready for you to confirm completion." />
                        </li>
                    ))}
                </ul>
            )}

            {opened && (
                <section aria-label="Open placement">
                    <h2>{opened.studentName} at {opened.companyName}</h2>
                    {detailError ? <p role="alert">Could not load this placement: {detailError}</p>
                        : !details ? <p>Loading...</p> : (
                        <>
                            <ProgressCard summary={summary} completionNote="Ready for you to confirm completion." />
                            <button type="button" disabled={!canComplete || busy} title={completeHint}
                                    onClick={() => complete(opened)}>
                                Complete placement
                            </button>
                            {!canComplete && completeHint && <span> {completeHint}.</span>}

                            <TimeLogTable
                                logs={details.logs}
                                renderActions={(log) => log.status === 'VERIFIED' && (
                                    unverifyingId === log.id ? (
                                        <div>
                                            <label htmlFor={`reason-${log.id}`}>Reason</label>
                                            <textarea id={`reason-${log.id}`} maxLength={MAX_REASON}
                                                      value={reasons[log.id] ?? ''}
                                                      onChange={(e) => setReasons((prev) => ({ ...prev, [log.id]: e.target.value }))} />
                                            <button type="button" disabled={busy || !(reasons[log.id] ?? '').trim()}
                                                    onClick={() => unverify(log)}>
                                                Un-verify
                                            </button>{' '}
                                            <button type="button" onClick={() => setUnverifyingId(null)}>Cancel</button>
                                        </div>
                                    ) : (
                                        <button type="button" disabled={busy} onClick={() => setUnverifyingId(log.id)}>
                                            Un-verify…
                                        </button>
                                    )
                                )}
                            />
                        </>
                    )}
                </section>
            )}
        </div>
    )
}

export default ProgressPage
