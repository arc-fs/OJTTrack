// Pure helpers for time logs: no React, no fetch, so they are easy to test (format.test.js).
//
// The shift and date rules MIRROR the backend (TimeLog.shiftProblem and
// TimeLogService.validate) and use the same messages. They only give early feedback:
// the server is always the authority, and its message is shown if it disagrees.

export const MAX_SHIFT_MINUTES = 960   // 16 h, V5 chk_time_logs_max_shift

const TIME = /^([01]\d|2[0-3]):([0-5]\d)(:\d{2})?$/   // "HH:mm" or "HH:mm:ss", 00:00-23:59

/** 510 -> "8 h 30 m", 480 -> "8 h", 0 -> "0 h". */
export function formatMinutes(minutes) {
    const total = Math.max(0, Math.round(Number(minutes) || 0))
    const hours = Math.floor(total / 60)
    const rest = total % 60
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} m`
}

/** The backend sends times as "HH:mm:ss"; <input type="time"> uses "HH:mm". */
export function toHHmm(time) {
    return typeof time === 'string' ? time.slice(0, 5) : ''
}

function toMinutes(time) {
    const match = TIME.exec(time ?? '')
    return match ? Number(match[1]) * 60 + Number(match[2]) : null
}

/** Minutes from time in to time out, breaks included; +24 h for an overnight shift. Null if a time is invalid. */
export function shiftMinutes(timeIn, timeOut, endsNextDay) {
    const start = toMinutes(timeIn)
    const end = toMinutes(timeOut)
    if (start === null || end === null) return null
    const minutes = end - start
    return endsNextDay ? minutes + 24 * 60 : minutes
}

/**
 * Same checks, in the same order and with the same messages, as TimeLog.shiftProblem.
 * Returns a message, or null when the shift is valid.
 */
export function shiftProblem({ timeIn, timeOut, endsNextDay, breakMinutes }) {
    const start = toMinutes(timeIn)
    const end = toMinutes(timeOut)
    if (start === null || end === null) {
        return 'Enter a valid time in and time out'   // frontend-only: a half-filled time input
    }
    if (start === end) {
        return "Time in and time out can't be the same"
    }
    const outIsEarlier = end < start
    if (outIsEarlier && !endsNextDay) {
        return "Time out is earlier than time in. If this shift ends the next day, tick 'Ends next day'; "
            + 'otherwise correct the times'
    }
    if (!outIsEarlier && endsNextDay) {
        return "'Ends next day' is ticked, but time out is later than time in on the same day"
    }
    const shift = shiftMinutes(timeIn, timeOut, endsNextDay)
    if (shift > MAX_SHIFT_MINUTES) {
        return "A shift can't be longer than 16 hours"
    }
    const breakValue = breakMinutes === '' || breakMinutes === null || breakMinutes === undefined
        ? NaN : Number(breakMinutes)
    if (!Number.isInteger(breakValue)) {
        return 'Enter the break in whole minutes (0 if none)'   // frontend-only: empty or non-numeric
    }
    if (breakValue < 0) {
        return "Break can't be negative"
    }
    if (breakValue >= shift) {
        return 'Break must be shorter than the shift'
    }
    return null
}

/** Today's date as "YYYY-MM-DD" in the browser's timezone; only for defaults and early feedback. */
export function todayIso(now = new Date()) {
    const pad = (n) => String(n).padStart(2, '0')
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** False when the placement starts after today, i.e. no date can be logged yet. */
export function canLogYet(placement, today) {
    return Boolean(placement) && placement.startDate <= today
}

/**
 * Date rules from TimeLogService.validate: from the placement's start date to today.
 * ISO dates ("YYYY-MM-DD") compare correctly as strings. Returns a message or null.
 */
export function dateProblem(logDate, placement, today) {
    if (!placement) return 'You have no active placement to log hours for'
    if (!canLogYet(placement, today)) {
        return `Your placement at ${placement.companyName} starts on ${placement.startDate}; you can log hours from then`
    }
    if (!logDate) return 'Choose a date'
    if (logDate < placement.startDate) {
        return `Your placement at ${placement.companyName} starts ${placement.startDate}. Choose a date from then on`
    }
    if (logDate > today) return "You can't log a future date"
    return null
}

/** Editable = PENDING or RETURNED, on an ACTIVE placement (same rule as TimeLogService.edit). */
export function isEditable(log, placement) {
    return Boolean(log && placement)
        && (log.status === 'PENDING' || log.status === 'RETURNED')
        && placement.status === 'ACTIVE'
}

/**
 * Which fields of the student's draft differ from the saved log (used to show an edit
 * conflict). Times are compared as "HH:mm"; accomplishments ignore surrounding spaces.
 */
export function changedFields(draft, saved) {
    const fields = []
    if (draft.logDate !== saved.logDate) fields.push('logDate')
    if (toHHmm(draft.timeIn) !== toHHmm(saved.timeIn)) fields.push('timeIn')
    if (toHHmm(draft.timeOut) !== toHHmm(saved.timeOut)) fields.push('timeOut')
    if (Boolean(draft.endsNextDay) !== Boolean(saved.endsNextDay)) fields.push('endsNextDay')
    if (Number(draft.breakMinutes) !== Number(saved.breakMinutes)) fields.push('breakMinutes')
    if ((draft.accomplishments ?? '').trim() !== (saved.accomplishments ?? '').trim()) fields.push('accomplishments')
    return fields
}

/** A placement's role, or the same honest wording everywhere for placements created before roles were recorded. */
export function roleText(role) {
    return role ?? 'Role not recorded'
}

const WEEK_MINUTES = 40 * 60   // one 5-day work week of 8-hour days

/**
 * The geometry of the hours ledger (ProgressCard), from the server's summary:
 * how much of the bar is verified, how much is pending review, where the weekly
 * ticks fall, and a full sentence for screen readers. Percentages are clamped so
 * the bar never overflows when verified hours exceed the requirement.
 */
export function hoursLedger({ verifiedMinutes = 0, pendingMinutes = 0, requiredMinutes = 0 }) {
    if (!(requiredMinutes > 0)) {
        return { verifiedPercent: 0, pendingPercent: 0, tickPercent: null, label: 'No required hours set' }
    }
    const verifiedPercent = Math.min(100, (verifiedMinutes / requiredMinutes) * 100)
    const pendingPercent = Math.min(100 - verifiedPercent, (pendingMinutes / requiredMinutes) * 100)
    const tickPercent = Math.min(100, (WEEK_MINUTES / requiredMinutes) * 100)
    const label = `${formatMinutes(verifiedMinutes)} of ${formatMinutes(requiredMinutes)} verified, `
        + `${formatMinutes(pendingMinutes)} pending review`
    return { verifiedPercent, pendingPercent, tickPercent, label }
}
