const ACTION_LABELS = {
    SUBMITTED: 'Submitted',
    CONFIRMED: 'Confirmed',
    RETURNED: 'Returned',
    UNCONFIRMED: 'Un-confirmed',
}

/**
 * A requirement's append-only history (requirement_events), oldest first. Each entry keeps
 * its own note or reason, so earlier rounds stay readable after a resubmission.
 * Collapsed by default with the native <details> element (keyboard accessible, no state).
 */
function RequirementHistory({ history }) {
    return (
        <details>
            <summary>History ({history.length})</summary>
            {history.length === 0 ? <p>No history yet.</p> : (
                <ol>
                    {history.map((event, index) => (
                        <li key={index}>
                            {new Date(event.createdAt).toLocaleString()} · {ACTION_LABELS[event.action] ?? event.action}
                            {event.actorName && <> by {event.actorName}</>}
                            {event.text && <>: {event.text}</>}
                        </li>
                    ))}
                </ol>
            )}
        </details>
    )
}

export default RequirementHistory
