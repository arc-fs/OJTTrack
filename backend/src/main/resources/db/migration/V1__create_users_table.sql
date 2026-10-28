CREATE TABLE users (
    id            INT GENERATED ALWAYS AS IDENTITY
                  (START WITH 1 INCREMENT BY 1)
                  PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20) NOT NULL
                  CHECK (role IN ('STUDENT', 'COORDINATOR')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);