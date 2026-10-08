# Project Comanche

Project Comanche is a Java 21, Spring Boot 4.1.1 multi-module IoT AIOps platform. The current implementation milestone provides the local infrastructure only; the application modules remain unchanged.

## Modules

- `comanche-iot-gateway` — the future MQTT-to-Kafka boundary.
- `comanche-operations-service` — the future owner of operational state in PostgreSQL.
- `comanche-aiops-service` — the future read-only incident investigation service.

The architecture and behavior are specified under `docs/specs/`.

## Prerequisites

- Java 21
- Maven 3.8 or newer
- Docker Engine with Docker Compose v2 (`docker compose`) or Compose v1 (`docker-compose`)

## Local infrastructure

The Compose environment starts:

| Service | Local endpoint | Persistence |
|---|---|---|
| Kafka (single-broker KRaft) | `localhost:29092` | `kafka-data` volume |
| Eclipse Mosquitto | `localhost:1883` | `mosquitto-data` and `mosquitto-logs` volumes |
| PostgreSQL | `localhost:5432` | `postgres-data` volume |

The defaults are intentionally local-development credentials. To override them, copy `.env.example` to `.env`, change the values, and keep `.env` out of version control.

Start the infrastructure:

```bash
docker compose up -d
```

The one-shot `kafka-init` service creates the seven canonical topics from SPEC-006. Each topic has one partition and replication factor one for the single-broker environment. Retention is 24 hours for telemetry, three days for device status, and seven days for all other initial topics.

Check readiness and topic initialization:

```bash
docker compose ps
docker compose logs kafka-init
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
docker compose exec postgres pg_isready -U comanche -d comanche
docker compose exec mosquitto mosquitto_sub \
  -h localhost -u comanche-local -P comanche-local \
  -t '$SYS/broker/version' -C 1 -W 5
```

If values were overridden in `.env`, use the corresponding PostgreSQL and MQTT credentials in verification commands.

Stop the containers while retaining local data:

```bash
docker compose down
```

To deliberately remove the containers and all named development volumes:

```bash
docker compose down --volumes
```

## Build verification

```bash
mvn clean verify
docker compose config
```

## M1 security and design boundaries

- Kafka uses plaintext listeners without authentication, as SPEC-006 permits for an isolated local environment. Secured Kafka integration testing and service-specific permissions are deferred by the specification.
- Mosquitto requires authentication, but M1 uses one shared local identity with access to `comanche/devices/#`. Per-device identities and topic ACLs require the gateway and simulator work that is outside this infrastructure-only milestone.
- The specifications leave exact Kafka partition counts and local replication factors open. M1 uses one partition and replication factor one, matching the single-broker topology and SPEC-006's allowed initial partition count.
- The checked-in defaults are not production credentials. TLS, secret management, high availability, application containers, and production hardening are outside M1.
