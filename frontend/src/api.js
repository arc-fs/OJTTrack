// Small wrapper around fetch for talking to the Spring Boot API.
//
// Spring Security (csrf.spa()) requires every POST/PUT/DELETE to carry an
// X-XSRF-TOKEN header whose value matches the XSRF-TOKEN cookie. The cookie is
// issued lazily, so we ask the backend for it (GET /api/auth/csrf) whenever
// it is missing — e.g. on first load, and after login/logout rotate it.

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

export class ApiError extends Error {
    constructor(status, message) {
        super(message)
        this.status = status
    }
}

function readCsrfCookie() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
    return match ? decodeURIComponent(match[1]) : null
}

async function csrfToken() {
    let token = readCsrfCookie()
    if (!token) {
        await fetch('/api/auth/csrf')
        token = readCsrfCookie()
    }
    return token
}

// The backend returns errors as Problem Details JSON: { "status": 400, "detail": "..." }.
// Use its "detail" when there is one; some responses (e.g. 401/403 from Spring
// Security) have no body, so fall back to the status code.
async function errorMessage(res) {
    try {
        const problem = await res.json()
        if (problem && problem.detail) return problem.detail
    } catch {
        // no JSON body
    }
    return 'Request failed (status ' + res.status + ')'
}

export async function apiFetch(url, { method = 'GET', headers = {}, body } = {}) {
    const finalHeaders = { ...headers }
    if (!SAFE_METHODS.includes(method)) {
        const token = await csrfToken()
        if (token) finalHeaders['X-XSRF-TOKEN'] = token
    }

    const res = await fetch(url, { method, headers: finalHeaders, body })
    if (!res.ok) {
        throw new ApiError(res.status, await errorMessage(res))
    }
    // Some endpoints (204 responses, login) return no JSON body.
    const contentType = res.headers.get('Content-Type') || ''
    return contentType.includes('application/json') ? res.json() : null
}

export function apiJson(url, method, data) {
    return apiFetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
    })
}

// Spring's formLogin expects form-encoded "username" and "password", not JSON.
export function login(email, password) {
    return apiFetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username: email, password }),
    })
}

export function logout() {
    return apiFetch('/api/auth/logout', { method: 'POST' })
}

export function getCurrentUser() {
    return apiFetch('/api/auth/me')
}
