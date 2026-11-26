-- School requirements and tasks (MVP §4.7).
-- A requirement is owned by the coordinator who created it; each student it applies to has
-- one assignment row (the CURRENT state) plus append-only history events.
-- Ownership is checked by the application on each request (like placements and time logs),
-- so nothing here ties an assignment to the student's CURRENT coordinator: reassigning a
-- student to another coordinator is never blocked by this schema.

CREATE TABLE requirements (
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coordinator_id INTEGER       NOT NULL REFERENCES users (id),   -- the owner
    title          VARCHAR(200)  NOT NULL CHECK (title ~ '[^[:space:]]'),
    description    VARCHAR(2000),
    due_date       DATE,                                   -- optional; overdue is computed, never stored
    archived_at    TIMESTAMPTZ,                            -- archived = read-only, hidden from active views
    version        INTEGER       NOT NULL DEFAULT 0,       -- optimistic locking (edit/archive/delete)
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_requirements_coordinator ON requirements (coordinator_id);

CREATE TABLE requirement_assignments (
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- CASCADE: deleting a requirement removes its untouched assignments in the same statement.
    -- Assignments WITH history can't be removed: requirement_events references them (RESTRICT).
    requirement_id INTEGER     NOT NULL REFERENCES requirements (id) ON DELETE CASCADE,
    student_id     INTEGER     NOT NULL REFERENCES users (id),
    status         VARCHAR(20) NOT NULL
                   CHECK (status IN ('NOT_STARTED', 'SUBMITTED', 'CONFIRMED', 'RETURNED')),
    note           VARCHAR(1000),          -- the student's LATEST submission note
    feedback       VARCHAR(500),           -- the coordinator's LATEST return / un-confirm reason
    submitted_at   TIMESTAMPTZ,            -- the latest submission time
    confirmed_by   INTEGER REFERENCES users (id),
    confirmed_at   TIMESTAMPTZ,
    version        INTEGER     NOT NULL DEFAULT 0,          -- optimistic locking
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_assignment_once UNIQUE (requirement_id, student_id),
    -- NOT_STARTED has no submission time; every other state has one
    CONSTRAINT chk_assignment_submitted_at CHECK (
        (status = 'NOT_STARTED'  AND submitted_at IS NULL)
     OR (status <> 'NOT_STARTED' AND submitted_at IS NOT NULL)),
    -- CONFIRMED has both confirmation fields; every other state has neither
    CONSTRAINT chk_assignment_confirmed CHECK (
        (status = 'CONFIRMED'  AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
     OR (status <> 'CONFIRMED' AND confirmed_by IS NULL     AND confirmed_at IS NULL)),
    -- RETURNED needs real feedback text
    CONSTRAINT chk_assignment_returned_feedback CHECK (
        status <> 'RETURNED' OR (feedback IS NOT NULL AND feedback ~ '[^[:space:]]'))
);

CREATE INDEX idx_assignments_student ON requirement_assignments (student_id);

-- Append-only history. Each row keeps its OWN copy of the note or reason, independent of the
-- assignment's latest note/feedback (which later submissions overwrite).
CREATE TABLE requirement_events (
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id INTEGER     NOT NULL REFERENCES requirement_assignments (id),   -- RESTRICT: never cascade
    action        VARCHAR(20) NOT NULL
                  CHECK (action IN ('SUBMITTED', 'CONFIRMED', 'RETURNED', 'UNCONFIRMED')),
    actor_id      INTEGER     NOT NULL REFERENCES users (id),
    text          VARCHAR(1000),          -- SUBMITTED: the note as submitted; RETURNED/UNCONFIRMED: the reason
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- a return or un-confirm must record why
    CONSTRAINT chk_event_reason CHECK (
        action NOT IN ('RETURNED', 'UNCONFIRMED') OR (text IS NOT NULL AND text ~ '[^[:space:]]'))
);

CREATE INDEX idx_requirement_events_assignment ON requirement_events (assignment_id);
