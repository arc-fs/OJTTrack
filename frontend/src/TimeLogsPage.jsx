import { useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import { canLogYet, dateProblem, todayIso } from './format.js'
import ProgressCard from './ProgressCard.jsx'
import { statusInfo } from './status.js'
import TimeLogForm from './TimeLogForm.jsx'
import TimeLogTable from './TimeLogTable.jsx'

/**
 * Loads everything the page shows, without touching React state (so it is safe to call
 * from an effect). Always asks for an explicit placementId: the summary endpoint returns
 * 404 when called without one and the student has no ACTIVE placement.
 */
async function fetchPageData(preferredId) {
    const placements = await apiFetch('/api/placements')
    const selected = placements.find((p) => p.id === preferredId)
        ?? placements.find((p) => p.status === 'ACTIVE')
        ?? placements[0]
        ?? null
    if (!selected) {
        return { placements, selectedId: null, logs: [], summary: null }
    }
    const [logs, summary] = await Promise.all([
        apiFetch(`/api/time-logs?placementId=${selected.id}`),
        apiFetch(`/api/time-logs/summary?placementId=${selected.id}`),
    ])
    return { placements, selectedId: selected.id, logs, summary }
}

/**
 * Student "Time logs" tab. Owns the loaded data; the form owns its own draft, so a
 * reload (after saving, after a 409, or when the tab becomes active) never wipes it.
 */
function TimeLogsPage({ active }) {
    const [data, setData] = useState({ placements: [], selectedId: null, logs: [], summary: null })
    const [dataVersion, setDataVersion] = useState(0)    // +1 after every completed reload
    const [requestedId, setRequestedId] = useState(null) // placement chosen in the selector
    const [reloadToken, setReloadToken] = useState(0)
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)
    const [form, setForm] = useState(null)               // null | { mode: 'create' | 'edit', log?, seq }
    const [notice, setNotice] = useState(null)

    useEffect(() => {
        if (!active) return undefined   // hidden tab: refresh when it becomes visible
        let cancelled = false
        fetchPageData(requestedId)
            .then((result) => {
                if (cancelled) return
                setData(result)
                setLoadError(null)
                setDataVersion((v) => v + 1)
            })
            .catch((err) => !cancelled && setLoadError(err.message))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [active, requestedId, reloadToken])

    const reload = () => setReloadToken((t) => t + 1)
    const today = todayIso()
    const selected = data.placements.find((p) => p.id === data.selectedId)
    const isActivePlacement = selected?.status === 'ACTIVE'

    function openForm(mode, log) {
        setNotice(null)
        setForm({ mode, log, seq: Date.now() })
    }

    function handleSaved(saved) {
        setForm(null)
        setNotice(`Saved your log for ${saved.logDate}. It is now ${statusInfo('log', saved.status).text.toLowerCase()}.`)
        reload()   // the server recalculates totals and the estimate
    }

    if (loading) return <p className="loading" role="status">Loading…</p>
    if (loadError) return <p role="alert">Could not load your time logs: {loadError}</p>
    if (data.placements.length === 0) {
        return <p className="empty">No placement yet. Request one in Applications &amp; placement.</p>
    }

    return (
        <div>
            <h1>Time logs</h1>

            {data.placements.length > 1 && (
                <>
                    <label htmlFor="placement-select">Placement</label>
                    <select id="placement-select" value={data.selectedId ?? ''} disabled={form !== null}
                            onChange={(e) => setRequestedId(Number(e.target.value))}>
                        {data.placements.map((p) => (
                            <option key={p.id} value={p.id}>{p.companyName} ({statusInfo('placement', p.status).text})</option>
                        ))}
                    </select>
                    {form !== null && <span> Finish or cancel the open form to switch placements.</span>}
                </>
            )}

            <section aria-label="Hours">
                <h2>{selected.companyName}</h2>
                <ProgressCard summary={data.summary}
                              completionNote="Waiting for your coordinator to confirm completion." />
            </section>

            <section aria-label="Logs">
            <h2>Logs</h2>
            {!isActivePlacement && <p>Placement ended: logs can&apos;t be changed.</p>}
            {notice && <p role="status">{notice}</p>}

            {isActivePlacement && form === null && (
                canLogYet(selected, today)
                    ? <button type="button" data-variant="primary" onClick={() => openForm('create')}>Add a log</button>
                    : <p>{dateProblem(today, selected, today)}.</p>
            )}

            {form !== null && (
                <TimeLogForm
                    key={form.seq}
                    editLog={form.mode === 'edit' ? form.log : null}
                    placementId={selected.id}
                    placements={data.placements}
                    logs={data.logs}
                    dataVersion={dataVersion}
                    today={today}
                    onSaved={handleSaved}
                    onCancel={() => setForm(null)}
                    onConflict={reload}
                />
            )}

            <TimeLogTable
                logs={data.logs}
                renderActions={isActivePlacement ? (log) => (
                    (log.status === 'PENDING' || log.status === 'RETURNED') && (
                        <button type="button" disabled={form !== null} onClick={() => openForm('edit', log)}>
                            {log.status === 'RETURNED' ? 'Fix and resubmit' : 'Edit'}
                        </button>
                    )
                ) : null}
            />
            </section>
        </div>
    )
}

export default TimeLogsPage
