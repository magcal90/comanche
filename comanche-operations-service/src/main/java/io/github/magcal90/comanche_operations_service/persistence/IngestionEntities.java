package io.github.magcal90.comanche_operations_service.persistence;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization")
class OrganizationEntity {
	@Id @Column(name = "organization_id") String organizationId;
	String name;
	String status;
	@Column(name = "created_at") Instant createdAt;
	@Column(name = "updated_at") Instant updatedAt;
	protected OrganizationEntity() { }
}

@Entity
@Table(name = "site")
class SiteEntity {
	@Id @Column(name = "site_id") String siteId;
	@Column(name = "organization_id") String organizationId;
	String name;
	String timezone;
	String status;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "operating_hours") String operatingHours;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "peak_periods") String peakPeriods;
	@Column(name = "created_at") Instant createdAt;
	@Column(name = "updated_at") Instant updatedAt;
	protected SiteEntity() { }
}

@Entity
@Table(name = "device")
class DeviceEntity {
	@Id @Column(name = "device_id") String deviceId;
	@Column(name = "site_id") String siteId;
	@Column(name = "device_type") String deviceType;
	@Column(name = "display_name") String displayName;
	String model;
	@Column(name = "firmware_version") String firmwareVersion;
	@Column(name = "application_version") String applicationVersion;
	@Column(name = "lifecycle_status") String lifecycleStatus;
	@Column(name = "created_at") Instant createdAt;
	@Column(name = "updated_at") Instant updatedAt;
	protected DeviceEntity() { }
}

@Entity
@Table(name = "device_current_state")
class DeviceCurrentStateEntity {
	@Id @Column(name = "device_id") String deviceId;
	@Column(name = "health_state") String healthState;
	@Column(name = "connectivity_state") String connectivityState;
	@Column(name = "operational_state") String operationalState;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "reported_status") String reportedStatus;
	@Column(name = "last_seen_at") Instant lastSeenAt;
	@Column(name = "status_observed_at") Instant statusObservedAt;
	@Column(name = "operational_state_observed_at") Instant operationalStateObservedAt;
	@Column(name = "last_status_event_id") String lastStatusEventId;
	@Column(name = "last_operational_event_id") String lastOperationalEventId;
	@Column(name = "updated_at") Instant updatedAt;
	@Version long version;
	protected DeviceCurrentStateEntity() { }
}

@Entity
@Table(name = "processed_event")
class ProcessedEventEntity {
	@Id @Column(name = "event_id") String eventId;
	@Column(name = "device_id") String deviceId;
	@Column(name = "source_message_id") String sourceMessageId;
	@Column(name = "event_type") String eventType;
	@Column(name = "source_topic") String sourceTopic;
	@Column(name = "payload_hash") String payloadHash;
	@Column(name = "processed_at") Instant processedAt;
	protected ProcessedEventEntity() { }
}

@Entity
@Table(name = "device_telemetry")
class DeviceTelemetryEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "telemetry_id") Long id;
	@Column(name = "event_id") String eventId;
	@Column(name = "device_id") String deviceId;
	@Column(name = "source_message_id") String sourceMessageId;
	@Column(name = "schema_version") String schemaVersion;
	@Column(name = "occurred_at") Instant occurredAt;
	@Column(name = "published_at") Instant publishedAt;
	@Column(name = "operations_received_at") Instant operationsReceivedAt;
	@Column(name = "correlation_id") String correlationId;
	@JdbcTypeCode(SqlTypes.JSON) String metrics;
	protected DeviceTelemetryEntity() { }
}

@Entity
@Table(name = "device_current_metric")
@IdClass(DeviceCurrentMetricId.class)
class DeviceCurrentMetricEntity {
	@Id @Column(name = "device_id") String deviceId;
	@Id @Column(name = "metric_name") String metricName;
	@Column(name = "value_type") String valueType;
	@Column(name = "numeric_value") BigDecimal numericValue;
	@Column(name = "text_value") String textValue;
	@Column(name = "observed_at") Instant observedAt;
	@Column(name = "source_event_id") String sourceEventId;
	@Column(name = "updated_at") Instant updatedAt;
	protected DeviceCurrentMetricEntity() { }
}

class DeviceCurrentMetricId implements Serializable {
	String deviceId;
	String metricName;
	public DeviceCurrentMetricId() { }
	@Override public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof DeviceCurrentMetricId that)) return false;
		return Objects.equals(deviceId, that.deviceId) && Objects.equals(metricName, that.metricName);
	}
	@Override public int hashCode() { return Objects.hash(deviceId, metricName); }
}

@Entity
@Table(name = "device_status_observation")
class DeviceStatusObservationEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "status_observation_id") Long id;
	@Column(name = "event_id") String eventId;
	@Column(name = "device_id") String deviceId;
	@Column(name = "source_message_id") String sourceMessageId;
	@Column(name = "occurred_at") Instant occurredAt;
	@Column(name = "published_at") Instant publishedAt;
	@Column(name = "operations_received_at") Instant operationsReceivedAt;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "status_payload") String statusPayload;
	protected DeviceStatusObservationEntity() { }
}

@Entity
@Table(name = "device_event_observation")
class DeviceEventObservationEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "device_event_observation_id") Long id;
	@Column(name = "event_id") String eventId;
	@Column(name = "device_id") String deviceId;
	@Column(name = "source_message_id") String sourceMessageId;
	@Column(name = "device_event_type") String deviceEventType;
	@Column(name = "occurred_at") Instant occurredAt;
	@Column(name = "published_at") Instant publishedAt;
	@Column(name = "operations_received_at") Instant operationsReceivedAt;
	@JdbcTypeCode(SqlTypes.JSON) String attributes;
	protected DeviceEventObservationEntity() { }
}

@Entity
@Table(name = "device_operational_state_history")
class DeviceOperationalStateHistoryEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "operational_state_history_id") Long id;
	@Column(name = "source_event_id") String sourceEventId;
	@Column(name = "device_id") String deviceId;
	@Column(name = "previous_state") String previousState;
	@Column(name = "new_state") String newState;
	@Column(name = "effective_at") Instant effectiveAt;
	@Column(name = "operations_received_at") Instant operationsReceivedAt;
	protected DeviceOperationalStateHistoryEntity() { }
}
