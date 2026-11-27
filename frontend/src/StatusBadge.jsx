import { TONE_GLYPHS, statusInfo } from './status.js'

/**
 * A small status tag: tinted background, sentence-case text, and a glyph per tone
 * (✓ done, ◷ waiting, ! problem). The glyph is decorative (aria-hidden); the text
 * carries the meaning for screen readers.
 */
function StatusBadge({ kind, status, archived }) {
    const { text, tone } = statusInfo(kind, status, { archived })
    const glyph = TONE_GLYPHS[tone]
    return (
        <span className={`badge badge-${tone}`}>
            {glyph && <span className="badge-glyph" aria-hidden="true">{glyph}</span>}
            {text}
        </span>
    )
}

export default StatusBadge
