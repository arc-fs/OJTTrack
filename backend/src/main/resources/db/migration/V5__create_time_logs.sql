-- Daily OJT time logs, plus the placement changes they need.
-- Assumption (MVP): all students and companies are in the Philippines (Asia/Manila,
-- UTC+8, no daylight-saving time), so log dates and times are stored as local
-- wall-clock values (DATE / TIME) and minute arithmetic on them is exact.

-- ---- placements ----

-- Lets time_logs reference (placement, student) pairs with a composite FK.
ALTER TABLE placements
    ADD CONSTRAINT uq_placements_id_student UNIQUE (id, student_id);

-- Who confirmed completion (the coordinator). Set exactly when COMPLETED.
ALTER TABLE placements
    ADD COLUMN completed_by INTEGER REFERENCES users (id);
ALTER TABLE placements
    ADD CONSTRAINT chk_placements_completed_by CHECK ((status = 'COMPLETED') = (completed_by IS NOT NULL));

-- ---- fix V4 (applied migrations are never edited) ----
-- The V4 version allowed a decided request with only ONE of the two decision fields.
-- A PENDING request has neither; a decided request has both.
ALTER TABLE placement_requests
    DROP CONSTRAINT chk_placement_requests_decision;
ALTER TABLE placement_requests
    ADD CONSTRAINT chk_placement_requests_decision CHECK (
        (status = 'PENDING'  AND decided_by IS NULL     AND decided_at IS NULL)
     OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL));

-- ---- time_logs ----

CREATE TABLE time_logs (
    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    placement_id     INTEGER       NOT NULL,
    student_id       INTEGER       NOT NULL,
    log_date         DATE          NOT NULL,          -- the day the shift STARTS
    time_in          TIME(0)       NOT NULL,
    time_out         TIME(0)       NOT NULL,
    ends_next_day    BOOLEAN       NOT NULL DEFAULT FALSE,
    break_minutes    INTEGER       NOT NULL DEFAULT 0 CHECK (break_minutes >= 0),
    accomplishments  VARCHAR(2000) NOT NULL,
    -- Calculated by PostgreSQL; no application code can store a different total.
    worked_minutes   INTEGER GENERATED ALWAYS AS (
        (EXTRACT(EPOCH FROM (time_out - time_in))::INTEGER / 60)
        + CASE WHEN ends_next_day THEN 1440 ELSE 0 END
        - break_minutes
    ) STORED,
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'RETURNED')),
    feedback         VARCHAR(500),
    verified_by      INTEGER REFERENCES users (id),
    verified_at      TIMESTAMPTZ,
    version          INTEGER       NOT NULL DEFAULT 0,   -- optimistic locking (JPA @Version)
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- the log's student is always the placement's student
    CONSTRAINT fk_time_logs_placement_student
        FOREIGN KEY (placement_id, student_id) REFERENCES placements (id, student_id),
    -- one (combined) log per placement per day; split shifts use the break for the gap
    CONSTRAINT ux_time_logs_one_per_day UNIQUE (placement_id, log_date),
    -- the overnight flag must match the times; equal times are ambiguous (0 h or 24 h)
    CONSTRAINT chk_time_logs_direction
        CHECK (time_out <> time_in AND ends_next_day = (time_out < time_in)),
    -- whole minutes only (TIME(0) drops fractions of a second, not seconds)
    CONSTRAINT chk_time_logs_whole_minutes
        CHECK (EXTRACT(SECOND FROM time_in) = 0 AND EXTRACT(SECOND FROM time_out) = 0),
    -- the break must be shorter than the shift
    CONSTRAINT chk_time_logs_worked CHECK (worked_minutes > 0),
    -- sanity limit on one shift (time in -> time out, breaks included): 16 h = 960 min.
    -- Independent of any daily credit cap (there is none).
    CONSTRAINT chk_time_logs_max_shift CHECK (
        EXTRACT(EPOCH FROM (time_out - time_in))::INTEGER / 60
        + CASE WHEN ends_next_day THEN 1440 ELSE 0 END <= 960),
    -- VERIFIED has both verification fields; every other status has neither
    CONSTRAINT chk_time_logs_verified CHECK (
        (status = 'VERIFIED'  AND verified_by IS NOT NULL AND verified_at IS NOT NULL)
     OR (status <> 'VERIFIED' AND verified_by IS NULL     AND verified_at IS NULL)),
    -- RETURNED needs real feedback text (at least one non-whitespace character)
    CONSTRAINT chk_time_logs_returned_feedback CHECK (
        status <> 'RETURNED' OR (feedback IS NOT NULL AND feedback ~ '[^[:space:]]'))
);

CREATE INDEX idx_time_logs_student_id ON time_logs (student_id);
