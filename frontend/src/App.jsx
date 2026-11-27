import { useEffect, useState } from 'react'
import { getCurrentUser, logout } from './api.js'
import ApplicationsPage from './ApplicationsPage.jsx'
import CoordinatorDashboard from './CoordinatorDashboard.jsx'
import CoordinatorPage from './CoordinatorPage.jsx'
import CoordinatorRequirementsPage from './CoordinatorRequirementsPage.jsx'
import Login from './Login.jsx'
import PlacementRequests from './PlacementRequests.jsx'
import ProgressPage from './ProgressPage.jsx'
import ReviewQueue from './ReviewQueue.jsx'
import StudentDashboard from './StudentDashboard.jsx'
import StudentRequirementsPage from './StudentRequirementsPage.jsx'
import Tabs from './Tabs.jsx'
import TimeLogsPage from './TimeLogsPage.jsx'

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

    if (error) {
        return (
            <div className="standalone">
                <p role="alert">Could not reach the server: {error}</p>
            </div>
        )
    }
    if (user === undefined) {
        return <div className="standalone"><p className="loading" role="status">Loading…</p></div>
    }
    if (user === null) return <Login onLoggedIn={handleLoggedIn} />

    // .app is the shell grid (styles/layout.css): wordmark, sections and account in a
    // sidebar on desktop, a top bar below 960 px. Tabs places its nav and panels in it.
    return (
        <div className="app">
            <div className="brand">OJTTrack</div>
            <header className="account" aria-label="Signed in">
                <span className="account-name">{user.fullName}</span>
                <span className="account-email">{user.email}</span>
                <button type="button" onClick={handleLogout}>Log out</button>
            </header>
            {/* key: a different user logging in starts with fresh tabs and no leftover drafts */}
            <Tabs key={user.id} tabs={user.role === 'STUDENT' ? STUDENT_TABS : COORDINATOR_TABS} />
        </div>
    )
}

// ApplicationsPage contains the placement section on purpose: the two share state
// (which applications are locked), so they stay together in one tab.
const STUDENT_TABS = [
    { id: 'dashboard', label: 'Dashboard', render: (active, goTo) => <StudentDashboard active={active} goTo={goTo} /> },
    { id: 'time-logs', label: 'Time logs', render: (active) => <TimeLogsPage active={active} /> },
    { id: 'requirements', label: 'Requirements', render: (active) => <StudentRequirementsPage active={active} /> },
    { id: 'applications', label: 'Applications & placement', render: () => <ApplicationsPage /> },
]

const COORDINATOR_TABS = [
    { id: 'dashboard', label: 'Dashboard', render: (active, goTo) => <CoordinatorDashboard active={active} goTo={goTo} /> },
    { id: 'review', label: 'Review queue', render: (active) => <ReviewQueue active={active} /> },
    { id: 'requirements', label: 'Requirements', render: (active) => <CoordinatorRequirementsPage active={active} /> },
    { id: 'progress', label: 'Progress', render: (active) => <ProgressPage active={active} /> },
    { id: 'requests', label: 'Placement requests', render: () => <PlacementRequests /> },
    { id: 'students', label: 'Students', render: () => <CoordinatorPage /> },
]

export default App
