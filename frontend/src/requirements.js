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

// ---- coordinator ----

export const MAX_TITLE = 200         // requirements.title VARCHAR(200)
export const MAX_DESCRIPTION = 2000  // requirements.description VARCHAR(2000)
export const MAX_REASON = 500        // requirement_assignments.feedback VARCHAR(500)

/** True once any student has submitted (a returned or confirmed one submitted first). */
export function anySubmitted(counts) {
    return counts.submitted + counts.returned + counts.confirmed > 0
}

/**
 * The list row's counts. Archived submissions can't be reviewed any more, so they keep
 * their number but aren't presented as work waiting for the coordinator.
 */
export function progressText(counts, archived) {
    const submitted = archived ? `${counts.submitted} submitted (not reviewed)` : `${counts.submitted} awaiting review`
    return `${counts.notStarted} not started · ${submitted} · ${counts.returned} returned · ${counts.confirmed} confirmed`
}

/** The meaning-change warning shown before editing a requirement students already submitted against. */
export function editWarning(counts) {
    if (!anySubmitted(counts)) return null
    const submitted = counts.submitted + counts.returned + counts.confirmed
    return `${submitted} student(s) have submitted and ${counts.confirmed} are confirmed. Editing changes what `
        + 'they submitted against. For a different requirement, archive this one and create a new one.'
}

/** Current students who don't have this requirement yet (the "Assign more students" choices). */
export function unassignedStudents(currentStudents, assignments) {
    const assigned = new Set(assignments.map((a) => a.studentId))
    return currentStudents.filter((s) => !assigned.has(s.id))
}

/** A blank New requirement form. `replacing` names the archived requirement a replacement is for. */
export function emptyDraft() {
    return { title: '', description: '', dueDate: '', audience: 'ALL', studentIds: [], replacing: null, droppedCount: 0 }
}

/**
 * Prefills the create form from a requirement that is being archived and replaced. Keeps only
 * students who are still this coordinator's (the server would refuse the others) and counts
 * the ones left out, so the form can say so.
 */
export function replacementDraft(requirement, assignments, currentStudents) {
    const current = new Set(currentStudents.map((s) => s.id))
    const assigned = assignments.map((a) => a.studentId)
    const kept = assigned.filter((id) => current.has(id))
    return {
        title: requirement.title,
        description: requirement.description ?? '',
        dueDate: requirement.dueDate ?? '',
        audience: 'CHOSEN',
        studentIds: kept,
        replacing: requirement.title,
        droppedCount: assigned.length - kept.length,
    }
}

/** What's wrong with a create draft before sending it, or null. The server checks again. */
export function draftProblem(draft) {
    if (!draft.title.trim()) return 'Enter a title.'
    if (draft.audience === 'CHOSEN' && draft.studentIds.length === 0) return 'Choose at least one student.'
    return null
}

/** The POST /api/coordinator/requirements body. studentIds null = all current students. */
export function createBody(draft) {
    return {
        title: draft.title.trim(),
        description: draft.description.trim() || null,
        dueDate: draft.dueDate || null,
        studentIds: draft.audience === 'ALL' ? null : draft.studentIds,
    }
}
