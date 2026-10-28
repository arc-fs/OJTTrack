CREATE TABLE applications (
    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id       INTEGER NOT NULL REFERENCES users (id),
    company_name     VARCHAR(200) NOT NULL,
    position         VARCHAR(150),
    company_website  VARCHAR(255),
    contact_person   VARCHAR(150),
    contact_email    VARCHAR(255),
    contact_phone    VARCHAR(50),
    status           VARCHAR(20) NOT NULL
                     CHECK (
                         status IN (
                             'PLANNED',
                             'APPLIED',
                             'INTERVIEW',
                             'OFFERED',
                             'ACCEPTED',
                             'REJECTED',
                             'WITHDRAWN'
                         )
                     ),
    application_date DATE,
    follow_up_date   DATE,
    interview_date   TIMESTAMPTZ,
    source           VARCHAR(100),
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_student_id
    ON applications (student_id);