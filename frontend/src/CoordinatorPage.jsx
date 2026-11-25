import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import PlacementRequests from './PlacementRequests.jsx'

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

function CoordinatorPage() {
    const [students, setStudents] = useState([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState(null)

    const [email, setEmail] = useState('')
    const [fullName, setFullName] = useState('')
    const [password, setPassword] = useState('')
    const [formError, setFormError] = useState(null)
    const [success, setSuccess] = useState(null)
    const [submitting, setSubmitting] = useState(false)

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

        const input = { email: email.trim().toLowerCase(), fullName: fullName.trim(), password }
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
        } catch (err) {
            setFormError('Could not create student: ' + err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div>
            <PlacementRequests />

            <h1>My Students</h1>

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
                <button type="submit" disabled={submitting}>
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
                <table>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Email</th>
                            <th>Created</th>
                        </tr>
                    </thead>
                    <tbody>
                        {students.map((s) => (
                            <tr key={s.id}>
                                <td>{s.fullName}</td>
                                <td>{s.email}</td>
                                <td>{new Date(s.createdAt).toLocaleDateString()}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </div>
    )
}

export default CoordinatorPage
