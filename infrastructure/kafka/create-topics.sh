#!/usr/bin/env bash
set -euo pipefail

bootstrap_server="kafka:9092"

create_topic() {
  local topic="$1"
  local retention_ms="$2"

  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$bootstrap_server" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 1 \
    --replication-factor 1 \
    --config "retention.ms=$retention_ms"
}

# SPEC-006 candidate local-development retention defaults.
create_topic comanche.device.telemetry.v1 86400000
create_topic comanche.device.status.v1 259200000
create_topic comanche.device.events.v1 604800000
create_topic comanche.alert.events.v1 604800000
create_topic comanche.incident.events.v1 604800000
create_topic comanche.command.requests.v1 604800000
create_topic comanche.command.results.v1 604800000
create_topic comanche.device.telemetry.dlt.v1 604800000
create_topic comanche.device.status.dlt.v1 604800000
create_topic comanche.device.events.dlt.v1 604800000

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --list
