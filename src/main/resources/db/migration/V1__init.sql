CREATE TABLE employee_transaction (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(32)  NOT NULL,
    client_id       VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    error_code      VARCHAR(50),
    error_message   VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    submitted_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_transaction_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_employee_transaction_status
        CHECK (status IN ('INITIALIZED', 'ACCEPTED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_employee_transaction_client ON employee_transaction (client_id);

CREATE TABLE employee (
    id              BIGSERIAL PRIMARY KEY,
    transaction_pk  BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    dob             DATE         NOT NULL,
    gender          VARCHAR(10)  NOT NULL,
    pan             VARCHAR(10)  NOT NULL,
    mobile_number   VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_employee_transaction FOREIGN KEY (transaction_pk) REFERENCES employee_transaction (id),
    CONSTRAINT uk_employee_transaction UNIQUE (transaction_pk),
    CONSTRAINT uk_employee_pan UNIQUE (pan),
    CONSTRAINT ck_employee_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_employee_pan CHECK (pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    CONSTRAINT ck_employee_mobile CHECK (mobile_number ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_employee_name CHECK (char_length(name) BETWEEN 2 AND 100)
);
