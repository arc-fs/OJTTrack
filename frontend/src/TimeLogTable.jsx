import { formatMinutes, toHHmm } from './format.js'

/**
 * A table of time logs (TimeLogResponse[]). renderActions(log) returns the buttons for
 * a row, so students and coordinators reuse the same table with different actions.
 */
function TimeLogTable({ logs, renderActions }) {
    if (logs.length === 0) return <p>No logs yet.</p>

    return (
        <table>
            <thead>
                <tr>
                    <th>Date</th>
                    <th>Time</th>
                    <th>Worked</th>
                    <th>Status</th>
                    <th>Accomplishments</th>
                    {renderActions && <th>Actions</th>}
                </tr>
            </thead>
            <tbody>
                {logs.map((log) => (
                    <tr key={log.id}>
                        <td>{log.logDate}</td>
                        <td>
                            {toHHmm(log.timeIn)}–{toHHmm(log.timeOut)}
                            {log.endsNextDay && ' (+1 day)'}
                            {log.breakMinutes > 0 && <> · break {log.breakMinutes} min</>}
                        </td>
                        <td>{formatMinutes(log.workedMinutes)}</td>
                        <td>
                            {log.status}
                            {log.status === 'VERIFIED' && log.verifiedByName && (
                                <> by {log.verifiedByName} on {new Date(log.verifiedAt).toLocaleDateString()}</>
                            )}
                            {log.status === 'RETURNED' && log.feedback && (
                                <p role="note"><strong>Coordinator feedback:</strong> {log.feedback}</p>
                            )}
                        </td>
                        <td>{log.accomplishments}</td>
                        {renderActions && <td>{renderActions(log)}</td>}
                    </tr>
                ))}
            </tbody>
        </table>
    )
}

export default TimeLogTable
