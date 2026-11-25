import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import StudentPlacement from './StudentPlacement.jsx'

const STATUSES = ['PLANNED', 'APPLIED', 'INTERVIEW', 'OFFERED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN']

function ApplicationsPage() {
    // This page owns all student data so the placement section and the application
    // list always agree (e.g. which applications are locked).
    const [applications, setApplications] = useState([])
    const [placements, setPlacements] = useState([])
    const [requests, setRequests] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('ALL')
    const [companyName, setCompanyName] = useState('')
    const [status, setStatus] = useState('PLANNED')
    const [formError, setFormError] = useState(null)

    useEffect(() => {
        Promise.all([
            apiFetch('/api/applications'),
            apiFetch('/api/placements'),
            apiFetch('/api/placement-requests'),
        ])
            .then(([apps, places, reqs]) => {
                setApplications(apps)
                setPlacements(places)
                setRequests(reqs)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // Applications behind a placement (current or past) or a pending request can't be
    // edited (the server returns 409). Map each locked application id to the reason shown.
    const lockReasons = new Map()
    for (const r of requests) {
        if (r.status === 'PENDING') lockReasons.set(r.applicationId, 'request pending')
    }
    for (const p of placements) {
        lockReasons.set(p.applicationId, p.status === 'ACTIVE' ? 'current placement' : 'past placement')
    }

    function handleRequestSubmitted(created) {
        setRequests((prev) => [created, ...prev])
    }

    function handleSubmit(event) {
        event.preventDefault()
        setFormError(null)
        const trimmed = companyName.trim()
        if (!trimmed) {
            setFormError('Please enter a company name.')
            return
        }
        apiJson('/api/applications', 'POST', { companyName: trimmed, status })
            .then((saved) => {
                setApplications((prev) => [...prev, saved])
                setCompanyName('')
            })
            .catch((err) => setFormError('Could not save: ' + err.message))
    }

    function handleDelete(id) {
        if (!window.confirm('Delete this application?')) return
        apiFetch('/api/applications/' + id, { method: 'DELETE' })
            .then(() => setApplications((prev) => prev.filter((app) => app.id !== id)))
            .catch((err) => setFormError('Could not delete: ' + err.message))
    }

    function handleStatusChange(app, newStatus) {
        apiJson('/api/applications/' + app.id, 'PUT', { companyName: app.companyName, status: newStatus })
            .then((updated) => {
                setApplications((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
            })
            .catch((err) => setFormError('Could not update: ' + err.message))
    }

    if (loading) return <p>Loading...</p>
    if (error) return <p>Could not load your data: {error}</p>
    const visible = applications.filter(
        (app) =>
            app.companyName.toLowerCase().includes(search.toLowerCase()) &&
            (statusFilter === 'ALL' || app.status === statusFilter)
    )
    return (
        <div>
            <StudentPlacement
                applications={applications}
                placements={placements}
                requests={requests}
                onSubmitted={handleRequestSubmitted}
            />

            <h1>My Applications</h1>

            <form onSubmit={handleSubmit}>
                <label htmlFor="company">Company name</label>
                <input
                    id="company"
                    maxLength={200}
                    value={companyName}
                    onChange={(e) => setCompanyName(e.target.value)}
                />
                <label htmlFor="status">Status</label>
                <select id="status" value={status} onChange={(e) => setStatus(e.target.value)}>
                    {STATUSES.map((s) => (
                        <option key={s} value={s}>{s}</option>
                    ))}
                </select>
                <button type="submit">Add application</button>
                {formError && <p>{formError}</p>}
            </form>
            <div>
                <label htmlFor="search">Search</label>
                <input id="search" value={search} onChange={(e) => setSearch(e.target.value)} />
                <label htmlFor="filter">Filter by status</label>
                <select id="filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                    <option value="ALL">ALL</option>
                    {STATUSES.map((s) => (
                        <option key={s} value={s}>{s}</option>
                    ))}
                </select>
            </div>
            {applications.length === 0 ? (
                <p>No applications yet.</p>
            ) : visible.length === 0 ? (
                <p>No applications match your filters.</p>
            ) : (
                <ul>
                    {visible.map((app) => {
                        const lockReason = lockReasons.get(app.id)
                        const locked = lockReason !== undefined
                        return (
                            <li key={app.id}>
                                {app.companyName}{' '}
                                <select
                                    value={app.status}
                                    disabled={locked}
                                    aria-label={'Status of ' + app.companyName}
                                    onChange={(e) => handleStatusChange(app, e.target.value)}
                                >
                                    {STATUSES.map((s) => (
                                        <option key={s} value={s}>{s}</option>
                                    ))}
                                </select>
                                <button disabled={locked} onClick={() => handleDelete(app.id)}>Delete</button>
                                {locked && <span> (Locked: {lockReason})</span>}
                            </li>
                        )
                    })}
                </ul>
            )}
        </div>
    )
}

export default ApplicationsPage
