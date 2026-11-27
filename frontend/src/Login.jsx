import { useState } from 'react'
import { login } from './api.js'

function Login({ onLoggedIn }) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await login(email, password)
            await onLoggedIn()
        } catch (err) {
            setError(err.status === 401 ? 'Invalid email or password.' : err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="standalone">
            <div className="standalone-card">
                <h1 className="brand">OJTTrack</h1>
                <p className="muted">Sign in with the account your coordinator gave you.</p>
                <form onSubmit={handleSubmit}>
                    <label htmlFor="email">Email</label>
                    <input
                        id="email"
                        type="email"
                        autoComplete="username"
                        required
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                    />
                    <label htmlFor="password">Password</label>
                    <input
                        id="password"
                        type="password"
                        autoComplete="current-password"
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                    <button type="submit" data-variant="primary" disabled={submitting}>
                        {submitting ? 'Logging in…' : 'Log in'}
                    </button>
                    {error && <p role="alert">{error}</p>}
                </form>
            </div>
        </div>
    )
}

export default Login
