import { useEffect, useState } from 'react'
import { apiFetch, apiJson } from './api.js'
import { sentenceCase } from './status.js'
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
    const [followUpDate, setFollowUpDate] = useState('')
    const [formError, setFormError] = useState(null)
    const [notice, setNotice] = useState(null)      // last success, e.g. "Added Acme."

    // One message at a time: a success replaces an old error and vice versa.
    function fail(text) {
        setNotice(null)
        setFormError(text)
    }

    function succeed(text) {
        setFormError(null)
        setNotice(text)
    }

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
            fail('Please enter a company name.')
            return
        }
        apiJson('/api/applications', 'POST', { companyName: trimmed, status, followUpDate: followUpDate || null })
            .then((saved) => {
                setApplications((prev) => [...prev, saved])
                setCompanyName('')
                setFollowUpDate('')
                succeed(`Added ${saved.companyName}.`)
            })
            .catch((err) => fail('Could not save: ' + err.message))
    }

    // PUT replaces every field, so each update sends the application's full current values;
    // otherwise changing only the status would erase the follow-up date.
    function updateApplication(app, changes) {
        const body = {
            companyName: app.companyName,
            status: app.status,
            followUpDate: app.followUpDate ?? null,
            ...changes,
        }
        apiJson('/api/applications/' + app.id, 'PUT', body)
            .then((updated) => {
                setApplications((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
                succeed(`Updated ${updated.companyName}.`)
            })
            .catch((err) => fail('Could not update: ' + err.message))
    }

    function handleDelete(app) {
        if (!window.confirm('Delete this application?')) return
        apiFetch('/api/applications/' + app.id, { method: 'DELETE' })
            .then(() => {
                setApplications((prev) => prev.filter((a) => a.id !== app.id))
                succeed(`Deleted ${app.companyName}.`)
            })
            .catch((err) => fail('Could not delete: ' + err.message))
    }

    function handleStatusChange(app, newStatus) {
        updateApplication(app, { status: newStatus })
    }

    function handleFollowUpChange(app, newDate) {
        updateApplication(app, { followUpDate: newDate || null })
    }

    if (loading) return <p className="loading" role="status">Loading…</p>
    if (error) return <p role="alert">Could not load your data: {error}</p>
    const visible = applications.filter(
        (app) =>
            app.companyName.toLowerCase().includes(search.toLowerCase()) &&
            (statusFilter === 'ALL' || app.status === statusFilter)
    )
    return (
        <div>
            <h1>Applications &amp; placement</h1>

            <StudentPlacement
                applications={applications}
                placements={placements}
                requests={requests}
                onSubmitted={handleRequestSubmitted}
            />

            <section aria-label="Add an application">
            <h2>Add an application</h2>
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
                        <option key={s} value={s}>{sentenceCase(s)}</option>
                    ))}
                </select>
                <label htmlFor="follow-up">Follow up on (optional)</label>
                <input id="follow-up" type="date" value={followUpDate}
                       onChange={(e) => setFollowUpDate(e.target.value)} />
                <button type="submit" data-variant="primary">Add application</button>
                {formError && <p role="alert">{formError}</p>}
                {notice && <p role="status">{notice}</p>}
            </form>
            </section>

            <section aria-label="My applications">
            <h2>My applications</h2>
            <div className="filters">
                <div>
                    <label htmlFor="search">Search</label>
                    <input id="search" value={search} onChange={(e) => setSearch(e.target.value)} />
                </div>
                <div>
                    <label htmlFor="filter">Filter by status</label>
                    <select id="filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                        <option value="ALL">All statuses</option>
                        {STATUSES.map((s) => (
                            <option key={s} value={s}>{sentenceCase(s)}</option>
                        ))}
                    </select>
                </div>
            </div>
            {applications.length === 0 ? (
                <p className="empty">No applications yet.</p>
            ) : visible.length === 0 ? (
                <p className="empty">No applications match your filters.</p>
            ) : (
                // card-list / card-title / card-actions: spans and classes that only change phone layout
                <ul className="card-list">
                    {visible.map((app) => {
                        const lockReason = lockReasons.get(app.id)
                        const locked = lockReason !== undefined
                        return (
                            <li key={app.id}>
                                <span className="card-title">{app.companyName}</span>{' '}
                                <select
                                    value={app.status}
                                    disabled={locked}
                                    aria-label={'Status of ' + app.companyName}
                                    onChange={(e) => handleStatusChange(app, e.target.value)}
                                >
                                    {STATUSES.map((s) => (
                                        <option key={s} value={s}>{sentenceCase(s)}</option>
                                    ))}
                                </select>{' '}
                                <label>
                                    Follow up{' '}
                                    <input type="date" value={app.followUpDate ?? ''} disabled={locked}
                                           aria-label={'Follow-up date for ' + app.companyName}
                                           onChange={(e) => handleFollowUpChange(app, e.target.value)} />
                                </label>{' '}
                                <span className="card-actions">
                                    <button data-variant="danger" disabled={locked} onClick={() => handleDelete(app)}>
                                        Delete
                                    </button>
                                    {locked && <span> (Locked: {lockReason})</span>}
                                </span>
                            </li>
                        )
                    })}
                </ul>
            )}
            </section>
        </div>
    )
}

export default ApplicationsPage
