import { describe, expect, it } from 'vitest'
import {
    anySubmitted,
    canSubmit,
    createBody,
    draftProblem,
    dueBadge,
    editWarning,
    emptyDraft,
    progressText,
    replacementDraft,
    statusLabel,
    unassignedStudents,
} from './requirements.js'

describe('statusLabel', () => {
    it('describes each status', () => {
        expect(statusLabel('NOT_STARTED')).toBe('Not started')
        expect(statusLabel('SUBMITTED')).toBe('Submitted, waiting for review')
        expect(statusLabel('CONFIRMED')).toBe('Confirmed')
        expect(statusLabel('RETURNED')).toBe('Returned, needs changes')
    })

    it('does not call an archived submission "waiting"', () => {
        expect(statusLabel('SUBMITTED', true)).toBe('Submitted, not reviewed (requirement archived)')
        expect(statusLabel('CONFIRMED', true)).toBe('Confirmed')
    })
})

describe('canSubmit', () => {
    const item = (status, archived = false) => ({ status, archived })

    it('allows a first submission and a resubmission after a return', () => {
        expect(canSubmit(item('NOT_STARTED'))).toBe(true)
        expect(canSubmit(item('RETURNED'))).toBe(true)
    })

    it('refuses while waiting for review or once confirmed', () => {
        expect(canSubmit(item('SUBMITTED'))).toBe(false)
        expect(canSubmit(item('CONFIRMED'))).toBe(false)
    })

    it('refuses anything on an archived requirement', () => {
        expect(canSubmit(item('NOT_STARTED', true))).toBe(false)
        expect(canSubmit(item('RETURNED', true))).toBe(false)
    })
})

describe('dueBadge', () => {
    it('shows only what the server flagged, overdue first', () => {
        expect(dueBadge({ overdue: true, dueSoon: false })).toBe('Overdue')
        expect(dueBadge({ overdue: false, dueSoon: true })).toBe('Due soon')
        expect(dueBadge({ overdue: false, dueSoon: false })).toBeNull()
    })
})

const counts = (notStarted, submitted, returned, confirmed) => ({ notStarted, submitted, returned, confirmed })

describe('progressText', () => {
    it('shows submissions as awaiting review while the requirement is active', () => {
        expect(progressText(counts(2, 1, 0, 3), false))
            .toBe('2 not started · 1 awaiting review · 0 returned · 3 confirmed')
    })

    it('keeps the numbers but drops "awaiting review" once archived', () => {
        expect(progressText(counts(2, 1, 0, 3), true))
            .toBe('2 not started · 1 submitted (not reviewed) · 0 returned · 3 confirmed')
    })
})

describe('editWarning and anySubmitted', () => {
    it('stays quiet while nobody has submitted', () => {
        expect(anySubmitted(counts(5, 0, 0, 0))).toBe(false)
        expect(editWarning(counts(5, 0, 0, 0))).toBeNull()
    })

    it('counts every student who submitted, including returned and confirmed ones', () => {
        expect(anySubmitted(counts(0, 0, 1, 0))).toBe(true)
        expect(editWarning(counts(1, 1, 0, 2))).toMatch(/^3 student\(s\) have submitted and 2 are confirmed\./)
    })
})

describe('unassignedStudents', () => {
    it('offers only current students without this requirement', () => {
        const students = [{ id: 10 }, { id: 11 }, { id: 12 }]
        expect(unassignedStudents(students, [{ studentId: 11 }, { studentId: 99 }]).map((s) => s.id))
            .toEqual([10, 12])
    })
})

describe('replacementDraft', () => {
    const moa = { title: 'Submit signed MOA', description: null, dueDate: '2027-01-31' }

    it('copies the requirement and keeps only students who are still yours', () => {
        const draft = replacementDraft(moa, [{ studentId: 10 }, { studentId: 20 }], [{ id: 10 }, { id: 11 }])
        expect(draft).toEqual({
            title: 'Submit signed MOA', description: '', dueDate: '2027-01-31', audience: 'CHOSEN',
            studentIds: [10], replacing: 'Submit signed MOA', droppedCount: 1,
        })
    })
})

describe('draftProblem and createBody', () => {
    it('needs a real title, and a student when choosing', () => {
        expect(draftProblem({ ...emptyDraft(), title: '   ' })).toBe('Enter a title.')
        expect(draftProblem({ ...emptyDraft(), title: 'MOA', audience: 'CHOSEN' })).toBe('Choose at least one student.')
        expect(draftProblem({ ...emptyDraft(), title: 'MOA' })).toBeNull()
    })

    it('sends null students for "all", trimmed text and null blanks', () => {
        expect(createBody({ ...emptyDraft(), title: '  MOA ', description: '  ' }))
            .toEqual({ title: 'MOA', description: null, dueDate: null, studentIds: null })
        expect(createBody({ ...emptyDraft(), title: 'MOA', audience: 'CHOSEN', studentIds: [10], dueDate: '2027-01-31' }))
            .toEqual({ title: 'MOA', description: null, dueDate: '2027-01-31', studentIds: [10] })
    })
})
