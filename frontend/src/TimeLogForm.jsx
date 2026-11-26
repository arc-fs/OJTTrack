import { useState } from 'react'
import { apiJson } from './api.js'
import {
    canLogYet, changedFields, dateProblem, formatMinutes, isEditable, shiftMinutes, shiftProblem, toHHmm,
} from './format.js'

const MAX_ACCOMPLISHMENTS = 2000   // time_logs.accomplishments VARCHAR(2000)

const LABELS = {
    logDate: 'Date', timeIn: 'Time in', timeOut: 'Time out',
    endsNextDay: 'Ends next day', breakMinutes: 'Break (min)', accomplishments: 'Accomplishments',
}

function draftFrom(log) {
    return {
        logDate: log.logDate, timeIn: toHHmm(log.timeIn), timeOut: toHHmm(log.timeOut),
        endsNextDay: log.endsNextDay, breakMinutes: log.breakMinutes, accomplishments: log.accomplishments,
    }
}

function emptyDraft(today) {
    return { logDate: today, timeIn: '', timeOut: '', endsNextDay: false, breakMinutes: 0, accomplishments: '' }
}

/**
 * Create or edit one time log. The draft lives here, so the page can refresh its data
 * (logs, summary, placements) without ever overwriting what the student typed.
 *
 * Conflicts (409): the server's message is shown, the page reloads its data, and the
 * student decides. Nothing is resubmitted automatically. `dataVersion` goes up after
 * every page reload; the form remembers it at the moment of the 409 and only compares
 * against data loaded AFTER that.
 */
function TimeLogForm({ editLog, placementId, placements, logs, dataVersion, today, onSaved, onCancel, onConflict }) {
    const isEdit = Boolean(editLog)
    const [draft, setDraft] = useState(() => (isEdit ? draftFrom(editLog) : emptyDraft(today)))
    const [baseVersion, setBaseVersion] = useState(editLog?.version)   // edit: the version the draft is based on
    const [targetId, setTargetId] = useState(placementId)               // create: the placement the form is for
    const [serverError, setServerError] = useState(null)
    const [conflictAt, setConflictAt] = useState(null)                  // dataVersion when a 409 happened
    const [submitting, setSubmitting] = useState(false)

    const placement = placements.find((p) => p.id === (isEdit ? editLog.placementId : targetId))
    const activePlacement = placements.find((p) => p.status === 'ACTIVE')
    const inConflict = conflictAt !== null
    const refreshed = inConflict && dataVersion > conflictAt
    const saved = isEdit ? logs.find((l) => l.id === editLog.id) : null

    // Edit: PENDING/RETURNED on an ACTIVE placement (same rule as the server). Create: placement ACTIVE.
    const editable = isEdit ? isEditable(saved ?? editLog, placement) : placement?.status === 'ACTIVE'
    // After a refresh shows the log can't be changed any more, the draft stays visible but read-only.
    const locked = refreshed && !editable

    const shiftError = shiftProblem(draft)
    const dateError = dateProblem(draft.logDate, placement, today)
    const textError = draft.accomplishments.trim() ? null : 'Describe what you did'
    const problem = shiftError ?? dateError ?? textError
    // An edit conflict always needs an explicit choice; a create conflict only blocks while
    // the target placement is not ACTIVE (e.g. after a transfer, until the student switches).
    const blockedByConflict = isEdit ? inConflict : inConflict && (!refreshed || !editable)
    const canSave = !problem && !submitting && !blockedByConflict && editable

    function update(field, value) {
        setDraft((prev) => ({ ...prev, [field]: value }))
    }

    async function handleSubmit(event) {
        event.preventDefault()
        if (!canSave) return
        setSubmitting(true)
        setServerError(null)
        const fields = {
            logDate: draft.logDate, timeIn: draft.timeIn, timeOut: draft.timeOut, endsNextDay: draft.endsNextDay,
            breakMinutes: Number(draft.breakMinutes), accomplishments: draft.accomplishments.trim(),
        }
        try {
            const result = isEdit
                ? await apiJson(`/api/time-logs/${editLog.id}`, 'PUT', { version: baseVersion, ...fields })
                : await apiJson('/api/time-logs', 'POST', { placementId: targetId, ...fields })
            onSaved(result)
        } catch (err) {
            setServerError(err.message)   // the server's own message, always shown
            if (err.status === 409) {
                setConflictAt(dataVersion)
                onConflict()               // page reloads placements, logs and summary
            }
        } finally {
            setSubmitting(false)
        }
    }

    // ---- conflict choices ----

    function keepMyChanges() {           // edit: base the draft on the saved version; student must press Save
        setBaseVersion(saved.version)
        setConflictAt(null)
        setServerError(null)
    }

    function useSavedVersion() {         // edit: discard the draft
        setDraft(draftFrom(saved))
        setBaseVersion(saved.version)
        setConflictAt(null)
        setServerError(null)
    }

    function logForActivePlacement() {   // create after a transfer: switch target, never submit
        setTargetId(activePlacement.id)
        setConflictAt(null)
        setServerError(null)
    }

    const shift = shiftError ? null : shiftMinutes(draft.timeIn, draft.timeOut, draft.endsNextDay)
    const differing = isEdit && saved ? changedFields(draft, saved) : []

    return (
        <form onSubmit={handleSubmit}>
            <h3>
                {isEdit ? `Editing your log for ${editLog.logDate}` : `Logging for: ${placement?.companyName ?? '—'}`}
            </h3>

            <label htmlFor="log-date">Date</label>
            <input id="log-date" type="date" value={draft.logDate} readOnly={locked}
                   min={placement?.startDate} max={today}
                   onChange={(e) => update('logDate', e.target.value)} />

            <label htmlFor="log-in">Time in</label>
            <input id="log-in" type="time" step="60" value={draft.timeIn} readOnly={locked}
                   onChange={(e) => update('timeIn', e.target.value)} />

            <label htmlFor="log-out">Time out</label>
            <input id="log-out" type="time" step="60" value={draft.timeOut} readOnly={locked}
                   onChange={(e) => update('timeOut', e.target.value)} />

            {/* Never ticked automatically: an earlier time out may be a typo, so the student confirms. */}
            <label>
                <input type="checkbox" checked={draft.endsNextDay} disabled={locked}
                       onChange={(e) => update('endsNextDay', e.target.checked)} />
                Ends next day (overnight shift)
            </label>

            <label htmlFor="log-break">Break (minutes)</label>
            <input id="log-break" type="number" min="0" step="1" value={draft.breakMinutes} readOnly={locked}
                   onChange={(e) => update('breakMinutes', e.target.value)} />

            <label htmlFor="log-text">Accomplishments</label>
            <textarea id="log-text" maxLength={MAX_ACCOMPLISHMENTS} value={draft.accomplishments} readOnly={locked}
                      onChange={(e) => update('accomplishments', e.target.value)} />

            <p>Split shift? Enter your first time in, your last time out, and the time in between as break.</p>

            {shift !== null && !locked && (
                <p>Shift {formatMinutes(shift)} · Worked {formatMinutes(shift - Number(draft.breakMinutes))}</p>
            )}
            {!locked && problem && <p role="alert">{problem}</p>}
            {serverError && <p role="alert"><strong>{serverError}</strong></p>}

            {inConflict && !refreshed && <p>Checking the latest saved data...</p>}

            {/* ---- edit conflict: show the saved version, let the student decide ---- */}
            {isEdit && refreshed && !saved && (
                <p>This log can no longer be found.</p>
            )}
            {isEdit && refreshed && saved && (
                <div role="group" aria-label="Saved version">
                    <h4>Saved version ({saved.status})</h4>
                    {saved.status === 'RETURNED' && saved.feedback && (
                        <p><strong>Coordinator feedback:</strong> {saved.feedback}</p>
                    )}
                    {differing.length === 0 ? (
                        <p>Your coordinator changed only the status: read the feedback above.</p>
                    ) : (
                        <ul>
                            {Object.keys(LABELS).map((field) => {
                                const value = field === 'endsNextDay' ? (saved[field] ? 'yes' : 'no')
                                    : field.startsWith('time') ? toHHmm(saved[field]) : String(saved[field])
                                const text = `${LABELS[field]}: ${value}`
                                return <li key={field}>{differing.includes(field) ? <mark>{text}</mark> : text}</li>
                            })}
                        </ul>
                    )}
                    {editable ? (
                        <>
                            <button type="button" onClick={keepMyChanges}>Keep my changes</button>{' '}
                            <button type="button" onClick={useSavedVersion}>Use the saved version</button>
                            <p>“Keep my changes” doesn’t save yet: check the form, then press Save.</p>
                        </>
                    ) : (
                        <p role="status">
                            {saved.status === 'VERIFIED'
                                ? 'This log was verified and can no longer be edited.'
                                : 'This placement has ended, so its logs are read-only. Your log is kept in its history.'}
                            {' '}Your draft is still shown above so you can copy it.
                        </p>
                    )}
                </div>
            )}

            {/* ---- create conflict: the active placement changed (transfer) or is gone ---- */}
            {!isEdit && refreshed && !editable && (
                <div role="group" aria-label="Placement changed">
                    {!activePlacement ? (
                        <p>You no longer have an active placement. Your draft is still shown above so you can copy it.</p>
                    ) : (
                        <>
                            <p>Your active placement is now <strong>{activePlacement.companyName}</strong>.</p>
                            {canLogYet(activePlacement, today) ? (
                                <button type="button" onClick={logForActivePlacement}>
                                    Log this for {activePlacement.companyName}
                                </button>
                            ) : (
                                <p>{dateProblem(draft.logDate, activePlacement, today)}.</p>
                            )}
                        </>
                    )}
                </div>
            )}

            {!locked && (
                <button type="submit" data-variant="primary" disabled={!canSave}>
                    {submitting ? 'Saving...'
                        : !isEdit ? 'Save log'
                            : (saved ?? editLog).status === 'RETURNED' ? 'Save and resubmit' : 'Save changes'}
                </button>
            )}{' '}
            <button type="button" onClick={onCancel} disabled={submitting}>{locked ? 'Close' : 'Cancel'}</button>
        </form>
    )
}

export default TimeLogForm
