import { formatMinutes, hoursLedger } from './format.js'

/**
 * Progress of one placement, exactly as the server calculated it (TimeLogSummaryResponse),
 * drawn as the hours ledger: verified hours solid, pending review hatched, the rest empty
 * with a tick every 40-hour week. Only verified hours count; the estimate is a prediction,
 * never stored. Used by the student's Dashboard and Time logs tab and the coordinator's
 * Progress tab; `compact` is the small version for lists and tables.
 */
function ProgressCard({ summary, completionNote, compact = false }) {
    if (!summary) return null
    const ledger = hoursLedger(summary)

    const bar = (
        <div className="ledger-bar" role="img" aria-label={ledger.label}
             style={ledger.tickPercent ? { '--tick': `${ledger.tickPercent}%` } : undefined}>
            <span className="ledger-verified" style={{ width: `${ledger.verifiedPercent}%` }} />
            <span className="ledger-pending"
                  style={{ left: `${ledger.verifiedPercent}%`, width: `${ledger.pendingPercent}%` }} />
        </div>
    )

    if (compact) {
        return (
            <div className="ledger ledger-compact">
                {bar}
                <p className="muted">
                    {formatMinutes(summary.verifiedMinutes)} of {formatMinutes(summary.requiredMinutes)} verified
                </p>
            </div>
        )
    }

    return (
        <div className="ledger">
            {bar}
            <ul className="ledger-facts">
                <li>
                    <span className="ledger-key ledger-key-verified" aria-hidden="true" />
                    <strong>{formatMinutes(summary.verifiedMinutes)}</strong> verified of{' '}
                    {formatMinutes(summary.requiredMinutes)} required
                </li>
                <li>
                    <span className="ledger-key ledger-key-pending" aria-hidden="true" />
                    <strong>{formatMinutes(summary.pendingMinutes)}</strong> pending review ({summary.pendingCount}{' '}
                    {summary.pendingCount === 1 ? 'log' : 'logs'})
                </li>
                <li>
                    <span className="ledger-key ledger-key-remaining" aria-hidden="true" />
                    <strong>{formatMinutes(summary.remainingMinutes)}</strong> remaining
                </li>
            </ul>
            {summary.returnedCount > 0 && (
                <p>Returned for correction: {summary.returnedCount} {summary.returnedCount === 1 ? 'log' : 'logs'}</p>
            )}
            {summary.placementStatus === 'COMPLETED' ? (
                <p><strong>Completed.</strong> The coordinator confirmed this placement.</p>
            ) : summary.requirementReached ? (
                <p><strong>Requirement reached.</strong> {completionNote}</p>
            ) : summary.estimateStatus === 'AVAILABLE' ? (
                <p className="muted">
                    Estimated completion: around {summary.estimatedCompletionDate}, based on verified logs up to{' '}
                    {summary.basedOnLogsUpTo}.
                </p>
            ) : (
                <p className="muted">Not enough verified data yet for an estimate.</p>
            )}
        </div>
    )
}

export default ProgressCard
