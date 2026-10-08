CREATE TABLE organization (
    organization_id varchar(100) PRIMARY KEY,
    name varchar(200) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE site (
    site_id varchar(100) PRIMARY KEY,
    organization_id varchar(100) NOT NULL REFERENCES organization(organization_id),
    name varchar(200) NOT NULL,
    timezone varchar(100) NOT NULL,
    status varchar(30) NOT NULL,
    operating_hours jsonb NOT NULL DEFAULT '{}'::jsonb,
    peak_periods jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE device (
    device_id varchar(100) PRIMARY KEY,
    site_id varchar(100) NOT NULL REFERENCES site(site_id),
    device_type varchar(40) NOT NULL,
    display_name varchar(200) NOT NULL,
    model varchar(100),
    firmware_version varchar(100),
    application_version varchar(100),
    lifecycle_status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE device_current_state (
    device_id varchar(100) PRIMARY KEY REFERENCES device(device_id),
    health_state varchar(30) NOT NULL DEFAULT 'HEALTHY',
    connectivity_state varchar(30) NOT NULL DEFAULT 'UNKNOWN',
    operational_state varchar(40) NOT NULL DEFAULT 'IDLE',
    reported_status jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_seen_at timestamptz,
    status_observed_at timestamptz,
    operational_state_observed_at timestamptz,
    last_status_event_id varchar(100),
    last_operational_event_id varchar(100),
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE processed_event (
    event_id varchar(100) PRIMARY KEY,
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    source_message_id varchar(100) NOT NULL,
    event_type varchar(80) NOT NULL,
    source_topic varchar(200) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL,
    CONSTRAINT uq_processed_event_logical_source UNIQUE (device_id, source_message_id)
);

CREATE TABLE device_telemetry (
    telemetry_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id varchar(100) NOT NULL UNIQUE REFERENCES processed_event(event_id),
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    source_message_id varchar(100) NOT NULL,
    schema_version varchar(20) NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    operations_received_at timestamptz NOT NULL,
    correlation_id varchar(100) NOT NULL,
    metrics jsonb NOT NULL,
    CONSTRAINT uq_device_telemetry_source UNIQUE (device_id, source_message_id)
);
CREATE INDEX ix_device_telemetry_device_time ON device_telemetry(device_id, occurred_at DESC);

CREATE TABLE device_current_metric (
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    metric_name varchar(150) NOT NULL,
    value_type varchar(20) NOT NULL,
    numeric_value numeric,
    text_value text,
    observed_at timestamptz NOT NULL,
    source_event_id varchar(100) NOT NULL REFERENCES processed_event(event_id),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (device_id, metric_name),
    CONSTRAINT ck_current_metric_one_value CHECK (
        (numeric_value IS NOT NULL AND text_value IS NULL) OR
        (numeric_value IS NULL AND text_value IS NOT NULL)
    )
);

CREATE TABLE device_status_observation (
    status_observation_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id varchar(100) NOT NULL UNIQUE REFERENCES processed_event(event_id),
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    source_message_id varchar(100) NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    operations_received_at timestamptz NOT NULL,
    status_payload jsonb NOT NULL,
    CONSTRAINT uq_device_status_source UNIQUE (device_id, source_message_id)
);

CREATE TABLE device_event_observation (
    device_event_observation_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id varchar(100) NOT NULL UNIQUE REFERENCES processed_event(event_id),
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    source_message_id varchar(100) NOT NULL,
    device_event_type varchar(80) NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    operations_received_at timestamptz NOT NULL,
    attributes jsonb NOT NULL,
    CONSTRAINT uq_device_event_source UNIQUE (device_id, source_message_id)
);

CREATE TABLE device_operational_state_history (
    operational_state_history_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_event_id varchar(100) NOT NULL UNIQUE REFERENCES processed_event(event_id),
    device_id varchar(100) NOT NULL REFERENCES device(device_id),
    previous_state varchar(40),
    new_state varchar(40) NOT NULL,
    effective_at timestamptz NOT NULL,
    operations_received_at timestamptz NOT NULL
);
