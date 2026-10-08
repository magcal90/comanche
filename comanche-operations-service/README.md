# Comanche Operations Service

The current milestone consumes the version 1 device telemetry, status, and device-event Kafka contracts. It stores immutable observations in PostgreSQL and maintains timestamp-guarded current projections. Delivery is at-least-once; durable event and logical-source identities make database processing idempotent.

## Deferred work

- Telemetry history is retained without an automatic purge during the MVP. A retention and archival policy requires a separately approved duration and operational design.
- Public REST APIs remain deferred until routes, DTOs, pagination, errors, and authorization are approved.
- Historical operational-state interval correction is not part of this milestone; late events remain available as historical observations.
