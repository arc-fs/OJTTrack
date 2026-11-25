import { useEffect, useState } from 'react'
import { getCurrentUser, logout } from './api.js'
import ApplicationsPage from './ApplicationsPage.jsx'
import Login from './Login.jsx'

function App() {
    // undefined = still checking the session, null = logged out, object = logged in
    const [user, setUser] = useState(undefined)
    const [error, setError] = useState(null)

    // Returns the logged-in user, or null when not logged in (401).
    function fetchUser() {
        return getCurrentUser().catch((err) => {
            if (err.status === 401) return null
            throw err
        })
    }

    useEffect(() => {
        fetchUser().then(setUser).catch((err) => setError(err.message))
    }, [])

    async function handleLoggedIn() {
        setUser(await fetchUser())
    }

    async function handleLogout() {
        try {
            await logout()
        } finally {
            setUser(null)
        }
    }

    if (error) return <p>Could not reach the server: {error}</p>
    if (user === undefined) return <p>Loading...</p>
    if (user === null) return <Login onLoggedIn={handleLoggedIn} />

    return (
        <div>
            <header>
                <span>Signed in as {user.fullName} ({user.email})</span>{' '}
                <button onClick={handleLogout}>Log out</button>
            </header>
            {user.role === 'STUDENT' ? (
                <ApplicationsPage />
            ) : (
                <p>The coordinator dashboard has not been built yet.</p>
            )}
        </div>
    )
}

export default App
