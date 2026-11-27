import { describe, expect, it } from 'vitest'
import {
    canLogYet,
    changedFields,
    dateProblem,
    formatMinutes,
    isEditable,
    hoursLedger,
    roleText,
    shiftMinutes,
    shiftProblem,
    toHHmm,
} from './format.js'

const shift = (timeIn, timeOut, endsNextDay = false, breakMinutes = 0) =>
    ({ timeIn, timeOut, endsNextDay, breakMinutes })

describe('formatMinutes', () => {
    it('formats whole and partial hours', () => {
        expect(formatMinutes(0)).toBe('0 h')
        expect(formatMinutes(480)).toBe('8 h')
        expect(formatMinutes(510)).toBe('8 h 30 m')
    })
})

describe('toHHmm', () => {
    it('drops the seconds the backend sends', () => {
        expect(toHHmm('08:00:00')).toBe('08:00')
        expect(toHHmm('22:30')).toBe('22:30')
        expect(toHHmm(null)).toBe('')
    })
})

describe('shiftProblem and shiftMinutes', () => {
    it('accepts a normal day shift', () => {
        expect(shiftProblem(shift('08:00', '17:00', false, 60))).toBeNull()
        expect(shiftMinutes('08:00', '17:00', false)).toBe(540)
    })

    it('accepts an overnight shift with "Ends next day" ticked', () => {
        expect(shiftProblem(shift('22:00', '06:00', true, 60))).toBeNull()
        expect(shiftMinutes('22:00', '06:00', true)).toBe(480)
    })

    it('rejects a same-day shift with "Ends next day" ticked', () => {
        expect(shiftProblem(shift('08:00', '17:00', true))).toMatch(/'Ends next day' is ticked/)
    })

    it('rejects overnight times without the tick', () => {
        expect(shiftProblem(shift('22:00', '06:00', false))).toMatch(/Time out is earlier than time in/)
    })

    it('allows exactly 16 hours overnight and rejects one minute more', () => {
        expect(shiftMinutes('20:00', '12:00', true)).toBe(960)
        expect(shiftProblem(shift('20:00', '12:00', true))).toBeNull()
        expect(shiftProblem(shift('20:00', '12:01', true))).toMatch(/16 hours/)
    })

    it('rejects a break longer than or equal to the shift', () => {
        expect(shiftProblem(shift('08:00', '12:00', false, 300))).toBe('Break must be shorter than the shift')
        expect(shiftProblem(shift('08:00', '12:00', false, 240))).toBe('Break must be shorter than the shift')
    })

    it('rejects equal times', () => {
        expect(shiftProblem(shift('08:00', '08:00'))).toMatch(/can't be the same/)
    })

    it('rejects invalid or incomplete times before doing any arithmetic', () => {
        expect(shiftProblem(shift('', '17:00'))).toBe('Enter a valid time in and time out')
        expect(shiftProblem(shift('08:00', ''))).toBe('Enter a valid time in and time out')
        expect(shiftProblem(shift('25:00', '17:00'))).toBe('Enter a valid time in and time out')
        expect(shiftProblem(shift(undefined, undefined))).toBe('Enter a valid time in and time out')
        expect(shiftMinutes('', '17:00', false)).toBeNull()
    })

    it('rejects a missing or negative break', () => {
        expect(shiftProblem(shift('08:00', '17:00', false, ''))).toMatch(/whole minutes/)
        // Not via shift(): its default parameter would turn undefined into 0.
        expect(shiftProblem({ timeIn: '08:00', timeOut: '17:00', endsNextDay: false })).toMatch(/whole minutes/)
        expect(shiftProblem(shift('08:00', '17:00', false, -5))).toBe("Break can't be negative")
    })
})

describe('dateProblem and canLogYet', () => {
    const newPlacement = { companyName: 'Beta Corp', startDate: '2026-12-10', status: 'ACTIVE' }
    const today = '2026-12-15'

    it('rejects a draft date from before the new placement started (transfer case)', () => {
        expect(dateProblem('2026-12-05', newPlacement, today))
            .toBe('Your placement at Beta Corp starts 2026-12-10. Choose a date from then on')
    })

    it('accepts the start date itself and dates up to today', () => {
        expect(dateProblem('2026-12-10', newPlacement, today)).toBeNull()
        expect(dateProblem('2026-12-15', newPlacement, today)).toBeNull()
    })

    it('rejects future and missing dates', () => {
        expect(dateProblem('2026-12-16', newPlacement, today)).toBe("You can't log a future date")
        expect(dateProblem('', newPlacement, today)).toBe('Choose a date')
    })

    it('says when nothing can be logged yet because the placement starts later', () => {
        const startsLater = { ...newPlacement, startDate: '2026-12-20' }
        expect(canLogYet(startsLater, today)).toBe(false)
        expect(dateProblem('2026-12-15', startsLater, today)).toMatch(/starts on 2026-12-20; you can log hours from then/)
        expect(canLogYet(newPlacement, today)).toBe(true)
    })
})

describe('isEditable', () => {
    const active = { status: 'ACTIVE' }
    it('allows PENDING and RETURNED logs on an ACTIVE placement only', () => {
        expect(isEditable({ status: 'PENDING' }, active)).toBe(true)
        expect(isEditable({ status: 'RETURNED' }, active)).toBe(true)
        expect(isEditable({ status: 'VERIFIED' }, active)).toBe(false)
        expect(isEditable({ status: 'RETURNED' }, { status: 'COMPLETED' })).toBe(false)
        expect(isEditable({ status: 'RETURNED' }, { status: 'TERMINATED' })).toBe(false)
    })
})

describe('changedFields', () => {
    const saved = {
        logDate: '2026-12-10', timeIn: '08:00:00', timeOut: '17:00:00', endsNextDay: false,
        breakMinutes: 60, accomplishments: 'Built the login page',
    }

    it('finds no differences when only the time format differs', () => {
        const draft = { ...saved, timeIn: '08:00', timeOut: '17:00' }
        expect(changedFields(draft, saved)).toEqual([])
    })

    it('reports changed times', () => {
        const draft = { ...saved, timeIn: '09:00', timeOut: '18:00' }
        expect(changedFields(draft, saved)).toEqual(['timeIn', 'timeOut'])
    })

    it('ignores surrounding spaces in accomplishments but not real changes', () => {
        expect(changedFields({ ...saved, accomplishments: '  Built the login page ' }, saved)).toEqual([])
        expect(changedFields({ ...saved, accomplishments: 'Fixed bugs' }, saved)).toEqual(['accomplishments'])
    })
})

describe('roleText', () => {
    it('shows the recorded role', () => {
        expect(roleText('Web Developer Intern')).toBe('Web Developer Intern')
    })

    it('says so when an older placement has no role', () => {
        expect(roleText(null)).toBe('Role not recorded')
        expect(roleText(undefined)).toBe('Role not recorded')
    })
})

describe('hoursLedger', () => {
    it('splits the bar into verified and pending, with a tick every 40-hour week', () => {
        const ledger = hoursLedger({ verifiedMinutes: 120 * 60, pendingMinutes: 16 * 60, requiredMinutes: 480 * 60 })
        expect(ledger.verifiedPercent).toBe(25)
        expect(ledger.pendingPercent).toBeCloseTo(3.333, 2)
        expect(ledger.tickPercent).toBeCloseTo(8.333, 2)   // 40 of 480 hours
        expect(ledger.label).toBe('120 h of 480 h verified, 16 h pending review')
    })

    it('never overflows when verified hours pass the requirement', () => {
        const ledger = hoursLedger({ verifiedMinutes: 500 * 60, pendingMinutes: 8 * 60, requiredMinutes: 480 * 60 })
        expect(ledger.verifiedPercent).toBe(100)
        expect(ledger.pendingPercent).toBe(0)
    })

    it('handles a placement without required hours', () => {
        expect(hoursLedger({ requiredMinutes: 0 })).toMatchObject({ verifiedPercent: 0, tickPercent: null })
    })
})
