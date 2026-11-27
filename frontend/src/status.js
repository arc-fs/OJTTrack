// How every status in the app is shown: sentence-case words and a tone.
// Tones: 'success' (officially done), 'waiting' (someone has to act),
// 'problem' (needs fixing), 'neutral' (informational). The badge adds a glyph
// per tone, so the meaning never depends on color alone.

const STATUSES = {
    // time logs
    'log:PENDING': ['Pending review', 'waiting'],
    'log:VERIFIED': ['Verified', 'success'],
    'log:RETURNED': ['Returned', 'problem'],
    // placements
    'placement:ACTIVE': ['Active', 'success'],
    'placement:COMPLETED': ['Completed', 'success'],
    'placement:TERMINATED': ['Ended', 'neutral'],
    // placement requests
    'request:PENDING': ['Waiting for approval', 'waiting'],
    'request:APPROVED': ['Approved', 'success'],
    'request:REJECTED': ['Rejected', 'problem'],
    // requirement assignments
    'requirement:NOT_STARTED': ['Not started', 'neutral'],
    'requirement:SUBMITTED': ['Submitted, waiting for review', 'waiting'],
    'requirement:CONFIRMED': ['Confirmed', 'success'],
    'requirement:RETURNED': ['Returned, needs changes', 'problem'],
    // due dates (from the server's overdue / dueSoon flags)
    'due:OVERDUE': ['Overdue', 'problem'],
    'due:DUE_SOON': ['Due soon', 'waiting'],
}

export const TONE_GLYPHS = { success: '✓', waiting: '◷', problem: '!', neutral: '' }

/** { text, tone } for a status of a given kind; unknown values fall back to sentence case. */
export function statusInfo(kind, status, { archived = false } = {}) {
    // An archived requirement can't be reviewed any more, so it isn't "waiting".
    if (kind === 'requirement' && archived && status === 'SUBMITTED') {
        return { text: 'Submitted, not reviewed (requirement archived)', tone: 'neutral' }
    }
    const known = STATUSES[`${kind}:${status}`]
    return known ? { text: known[0], tone: known[1] } : { text: sentenceCase(status), tone: 'neutral' }
}

/** "WITHDRAWN" → "Withdrawn", "NOT_STARTED" → "Not started". */
export function sentenceCase(value) {
    if (!value) return ''
    const words = String(value).replace(/_/g, ' ').toLowerCase()
    return words.charAt(0).toUpperCase() + words.slice(1)
}
