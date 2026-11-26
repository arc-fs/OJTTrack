import { formatMinutes } from './format.js'

/**
 * Progress of one placement, exactly as the server calculated it (TimeLogSummaryResponse).
 * Only verified hours count; the estimate is a prediction, never stored.
 * Used by the student's Time logs tab and the coordinator's Progress tab.
 */
function ProgressCard({ summary, completionNote }) {
    if (!summary) return null

    return (
        <div>
            <p>
                <strong>{formatMinutes(summary.verifiedMinutes)}</strong> verified of{' '}
                {formatMinutes(summary.requiredMinutes)} required · {formatMinutes(summary.remainingMinutes)} remaining
            </p>
            <p>
                Pending review: {formatMinutes(summary.pendingMinutes)} ({summary.pendingCount}{' '}
                {summary.pendingCount === 1 ? 'log' : 'logs'})
                {summary.returnedCount > 0 && <> · Returned for correction: {summary.returnedCount}</>}
            </p>
            {summary.placementStatus === 'COMPLETED' ? (
                <p><strong>Completed.</strong> The coordinator confirmed this placement.</p>
            ) : summary.requirementReached ? (
                <p><strong>Requirement reached.</strong> {completionNote}</p>
            ) : summary.estimateStatus === 'AVAILABLE' ? (
                <p>
                    Estimated completion: ~{summary.estimatedCompletionDate} (based on verified logs up to{' '}
                    {summary.basedOnLogsUpTo})
                </p>
            ) : (
                <p>Not enough verified data yet for an estimate.</p>
            )}
        </div>
    )
}

export default ProgressCard
