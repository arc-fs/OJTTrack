-- OJT placements and the requests that create or change them.
-- Business rules enforced here (the service enforces the rest):
--   * a placement always comes from one application, and an application gives at most one placement
--   * placements.student_id always matches the application's student (composite FK)
--   * a student has at most one ACTIVE placement
--   * a student has at most one PENDING placement request
--   * history rows are never deleted out from under their references (FKs default to RESTRICT)

-- Lets the composite foreign keys below reference (application, student) pairs.
ALTER TABLE applications
    ADD CONSTRAINT uq_applications_id_student UNIQUE (id, student_id);

CREATE TABLE placements (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  INTEGER     NOT NULL UNIQUE,
    student_id      INTEGER     NOT NULL,
    status          VARCHAR(20) NOT NULL
                    CHECK (status IN ('ACTIVE', 'COMPLETED', 'TERMINATED')),
    start_date      DATE        NOT NULL,
    end_date        DATE        NOT NULL,
    required_hours  INTEGER     NOT NULL CHECK (required_hours > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    CONSTRAINT fk_placements_application_student
        FOREIGN KEY (application_id, student_id) REFERENCES applications (id, student_id),
    CONSTRAINT chk_placements_dates CHECK (end_date >= start_date),
    -- ended_at is set exactly when the placement is no longer ACTIVE
    CONSTRAINT chk_placements_ended_at CHECK ((status = 'ACTIVE') = (ended_at IS NULL))
);

CREATE UNIQUE INDEX ux_placements_one_active_per_student
    ON placements (student_id)
    WHERE status = 'ACTIVE';

CREATE TABLE placement_requests (
    id                    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id            INTEGER     NOT NULL,
    application_id        INTEGER     NOT NULL,
    -- NULL: the student has no placement yet (activation). Otherwise: the placement to replace (change).
    current_placement_id  INTEGER REFERENCES placements (id),
    status                VARCHAR(20) NOT NULL
                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reason                VARCHAR(500),
    decision_note         VARCHAR(500),
    decided_by            INTEGER REFERENCES users (id),
    decided_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_placement_requests_application_student
        FOREIGN KEY (application_id, student_id) REFERENCES applications (id, student_id),
    -- a decision is recorded exactly when the request is no longer PENDING
    CONSTRAINT chk_placement_requests_decision
        CHECK ((status = 'PENDING') = (decided_by IS NULL AND decided_at IS NULL))
);

CREATE UNIQUE INDEX ux_placement_requests_one_pending_per_student
    ON placement_requests (student_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_placement_requests_application_id
    ON placement_requests (application_id);
