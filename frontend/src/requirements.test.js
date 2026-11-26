import { describe, expect, it } from 'vitest'
import { canSubmit, dueBadge, statusLabel } from './requirements.js'

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
