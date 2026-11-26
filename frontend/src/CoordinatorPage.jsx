import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'

// Mirrors the backend rules in CreateStudentRequest, so most mistakes are caught
// before a request is sent. The server still validates everything: these checks
// are only for a faster, friendlier form.
const MIN_PASSWORD = 8
const MAX_PASSWORD = 72
const MAX_EMAIL = 255
const MAX_NAME = 150

function validate({ email, fullName, password }) {
    if (!email) return 'Please enter an email.'
    if (!/^[^\s@]+@[^\s@]+$/.test(email)) return 'Please enter a valid email address.'
    if (!fullName) return 'Please enter the student’s full name.'
    if (password.length < MIN_PASSWORD) return `Password must be at least ${MIN_PASSWORD} characters.`
    return null
}

const SEARCH_START_HELP = 'From the placement search start date, a student who still has no active or completed '
    + 'placement appears under "Needs attention" on your dashboard as "No placement yet". '
    + 'Leave it empty to never flag them.'

function CoordinatorPage() {
    const [students, setStudents] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)

    const [email, setEmail] = useState('')
    const [fullName, setFullName] = useState('')
    const [password, setPassword] = useState('')
    const [searchStart, setSearchStart] = useState('')
    const [formError, setFormError] = useState(null)
    const [success, setSuccess] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    // Per-row edits of the search start date, kept apart from the loaded list (drafts).
    const [dateDrafts, setDateDrafts] = useState({})   // studentId -> 'YYYY-MM-DD' or ''
    const [rowMessage, setRowMessage] = useState(null)

    useEffect(() => {
        apiFetch('/api/coordinator/students')
            .then((data) => setStudents(data))
            .catch((err) => setLoadError(err.message))
            .finally(() => setLoading(false))
    }, [])

    async function handleSubmit(event) {
        event.preventDefault()
        setFormError(null)
        setSuccess(null)

        const input = {
            email: email.trim().toLowerCase(), fullName: fullName.trim(), password,
            placementSearchStartsOn: searchStart || null,
        }
        const problem = validate(input)
        if (problem) {
            setFormError(problem)
            return
        }

        setSubmitting(true)
        try {
            const created = await apiJson('/api/coordinator/students', 'POST', input)
            setStudents((prev) =>
                [...prev, created].sort((a, b) => a.fullName.localeCompare(b.fullName)))
            setSuccess(`Created ${created.fullName}. Share the email and temporary password with them.`)
            setEmail('')
            setFullName('')
            setPassword('')
            setSearchStart('')
        } catch (err) {
            setFormError('Could not create student: ' + err.message)
        } finally {
            setSubmitting(false)
        }
    }

    async function saveSearchStart(student, date) {
        setRowMessage(null)
        try {
            const updated = await apiJson(`/api/coordinator/students/${student.id}/placement-search-start`, 'PUT',
                { date: date || null })
            setStudents((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
            setDateDrafts((prev) => {
                const next = { ...prev }
                delete next[student.id]
                return next
            })
            setRowMessage({ kind: 'status', text: date
                ? `${student.fullName}: placement search starts on ${date}.`
                : `${student.fullName}: search start date cleared; they won't be flagged as "No placement yet".` })
        } catch (err) {
            setRowMessage({ kind: 'alert', text: `Could not update ${student.fullName}: ${err.message}` })
        }
    }

    return (
        <div>
            <h1>Students</h1>

            <form onSubmit={handleSubmit}>
                <h2>Create a student account</h2>
                <label htmlFor="student-email">Email</label>
                <input
                    id="student-email"
                    type="email"
                    maxLength={MAX_EMAIL}
                    autoComplete="off"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />
                <label htmlFor="student-name">Full name</label>
                <input
                    id="student-name"
                    maxLength={MAX_NAME}
                    autoComplete="off"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                />
                <label htmlFor="student-password">Temporary password</label>
                <input
                    id="student-password"
                    type="password"
                    minLength={MIN_PASSWORD}
                    maxLength={MAX_PASSWORD}
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />
                <label htmlFor="student-search-start">Placement search starts on (optional)</label>
                <input
                    id="student-search-start"
                    type="date"
                    value={searchStart}
                    onChange={(e) => setSearchStart(e.target.value)}
                />
                <p>{SEARCH_START_HELP}</p>
                <button type="submit" data-variant="primary" disabled={submitting}>
                    {submitting ? 'Creating...' : 'Create student'}
                </button>
                {formError && <p role="alert">{formError}</p>}
                {success && <p role="status">{success}</p>}
            </form>

            <h2>Students</h2>
            {loading ? (
                <p>Loading...</p>
            ) : loadError ? (
                <p role="alert">Could not load students: {loadError}</p>
            ) : students.length === 0 ? (
                <p>No students yet. Create one above.</p>
            ) : (
                <>
                    <p>{SEARCH_START_HELP}</p>
                    {rowMessage && <p role={rowMessage.kind}>{rowMessage.text}</p>}
                    {/* stack-table: a card per student on phones; .cell-label is shown only there */}
                    <table className="stack-table">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Email</th>
                                <th>Created</th>
                                <th>Placement search starts on</th>
                            </tr>
                        </thead>
                        <tbody>
                            {students.map((s) => {
                                const draft = dateDrafts[s.id] ?? s.placementSearchStartsOn ?? ''
                                const changed = draft !== (s.placementSearchStartsOn ?? '')
                                return (
                                    <tr key={s.id}>
                                        <td>{s.fullName}</td>
                                        <td><span className="cell-label">Email</span>{s.email}</td>
                                        <td>
                                            <span className="cell-label">Created</span>
                                            {new Date(s.createdAt).toLocaleDateString()}
                                        </td>
                                        <td className="actions">
                                            <span className="cell-label">Placement search starts on</span>
                                            <input type="date" value={draft}
                                                   aria-label={`Placement search start for ${s.fullName}`}
                                                   onChange={(e) => setDateDrafts((prev) => ({ ...prev, [s.id]: e.target.value }))} />
                                            <button type="button" disabled={!changed}
                                                    onClick={() => saveSearchStart(s, draft)}>Save</button>{' '}
                                            <button type="button" disabled={!s.placementSearchStartsOn}
                                                    onClick={() => saveSearchStart(s, '')}>Clear</button>
                                        </td>
                                    </tr>
                                )
                            })}
                        </tbody>
                    </table>
                </>
            )}
        </div>
    )
}

export default CoordinatorPage
