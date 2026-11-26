// Display helpers for school requirements (MVP §4.7). The SERVER decides overdue and
// due soon; these functions only turn its facts into words and decide which buttons
// to show. They are pure, so requirements.test.js can check them without a browser.

export const MAX_NOTE = 1000   // requirement_assignments.note VARCHAR(1000)

const STATUS_LABELS = {
    NOT_STARTED: 'Not started',
    SUBMITTED: 'Submitted, waiting for review',
    CONFIRMED: 'Confirmed',
    RETURNED: 'Returned, needs changes',
}

/** Words for a status. An archived requirement can't be reviewed, so SUBMITTED isn't "waiting". */
export function statusLabel(status, archived = false) {
    if (archived && status === 'SUBMITTED') return 'Submitted, not reviewed (requirement archived)'
    return STATUS_LABELS[status] ?? status
}

/** Same rule as StudentRequirementService.submit: not archived, and NOT_STARTED or RETURNED. */
export function canSubmit(item) {
    return !item.archived && (item.status === 'NOT_STARTED' || item.status === 'RETURNED')
}

/** The badge text for the server's overdue / dueSoon flags, or null for none. */
export function dueBadge(item) {
    if (item.overdue) return 'Overdue'
    if (item.dueSoon) return 'Due soon'
    return null
}
