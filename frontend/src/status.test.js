import { describe, expect, it } from 'vitest'
import { TONE_GLYPHS, sentenceCase, statusInfo } from './status.js'

describe('statusInfo', () => {
    it('gives each time-log status words and a tone', () => {
        expect(statusInfo('log', 'PENDING')).toEqual({ text: 'Pending review', tone: 'waiting' })
        expect(statusInfo('log', 'VERIFIED')).toEqual({ text: 'Verified', tone: 'success' })
        expect(statusInfo('log', 'RETURNED')).toEqual({ text: 'Returned', tone: 'problem' })
    })

    it('covers placements, requests, requirements and due dates', () => {
        expect(statusInfo('placement', 'TERMINATED')).toEqual({ text: 'Ended', tone: 'neutral' })
        expect(statusInfo('request', 'REJECTED').tone).toBe('problem')
        expect(statusInfo('requirement', 'CONFIRMED')).toEqual({ text: 'Confirmed', tone: 'success' })
        expect(statusInfo('due', 'OVERDUE')).toEqual({ text: 'Overdue', tone: 'problem' })
    })

    it('does not call an archived submission "waiting"', () => {
        expect(statusInfo('requirement', 'SUBMITTED', { archived: true }))
            .toEqual({ text: 'Submitted, not reviewed (requirement archived)', tone: 'neutral' })
        expect(statusInfo('requirement', 'CONFIRMED', { archived: true }).tone).toBe('success')
    })

    it('shows an unknown status in sentence case instead of hiding it', () => {
        expect(statusInfo('log', 'SOMETHING_NEW')).toEqual({ text: 'Something new', tone: 'neutral' })
    })
})

describe('glyphs and sentence case', () => {
    it('marks every tone except neutral with a shape', () => {
        expect(TONE_GLYPHS.success).toBe('✓')
        expect(TONE_GLYPHS.waiting).toBe('◷')
        expect(TONE_GLYPHS.problem).toBe('!')
        expect(TONE_GLYPHS.neutral).toBe('')
    })

    it('turns enum values into readable words', () => {
        expect(sentenceCase('WITHDRAWN')).toBe('Withdrawn')
        expect(sentenceCase('NOT_STARTED')).toBe('Not started')
        expect(sentenceCase(null)).toBe('')
    })
})
