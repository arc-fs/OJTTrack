import { useEffect, useState } from 'react'

const STATUSES = ['PLANNED', 'APPLIED', 'INTERVIEW', 'OFFERED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN']

function App() {
    const [applications, setApplications] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('ALL')
    const [companyName, setCompanyName] = useState('')
    const [status, setStatus] = useState('PLANNED')
    const [formError, setFormError] = useState(null)

    useEffect(() => {
        fetch('/api/applications')
            .then((res) => {
                if (!res.ok) throw new Error('Request failed: ' + res.status)
                return res.json()
            })
            .then((data) => setApplications(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    function handleSubmit(event) {
        event.preventDefault()
        setFormError(null)
        fetch('/api/applications', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ companyName, status }),
        })
            .then((res) => {
                if (!res.ok) throw new Error('Could not save (status ' + res.status + ')')
                return res.json()
            })
            .then((saved) => {
                setApplications((prev) => [...prev, saved])
                setCompanyName('')
            })
            .catch((err) => setFormError(err.message))
    }

    function handleDelete(id) {
        if (!window.confirm('Delete this application?')) return
        fetch('/api/applications/' + id, { method: 'DELETE' })
            .then((res) => {
                if (!res.ok) throw new Error('Could not delete (status ' + res.status + ')')
                setApplications((prev) => prev.filter((app) => app.id !== id))
            })
            .catch((err) => setFormError(err.message))
    }

    function handleStatusChange(app, newStatus) {
        fetch('/api/applications/' + app.id, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ companyName: app.companyName, status: newStatus }),
        })
            .then((res) => {
                if (!res.ok) throw new Error('Could not update (status ' + res.status + ')')
                return res.json()
            })
            .then((updated) => {
                setApplications((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
            })
            .catch((err) => setFormError(err.message))
    }
    if (loading) return <p>Loading...</p>
    if (error) return <p>Could not load applications: {error}</p>
    const visible = applications.filter(
        (app) =>
            app.companyName.toLowerCase().includes(search.toLowerCase()) &&
            (statusFilter === 'ALL' || app.status === statusFilter)
    )
    return (
        <div>
            <h1>My Applications</h1>

            <form onSubmit={handleSubmit}>
                <label htmlFor="company">Company name</label>
                <input
                    id="company"
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
                    {visible.map((app) => (
                        <li key={app.id}>
                            {app.companyName}{' '}
                            <select
                                value={app.status}
                                onChange={(e) => handleStatusChange(app, e.target.value)}
                            >
                                {STATUSES.map((s) => (
                                    <option key={s} value={s}>{s}</option>
                                ))}
                            </select>
                            <button onClick={() => handleDelete(app.id)}>Delete</button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default App