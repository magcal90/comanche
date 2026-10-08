package io.github.magcal90.comanche_operations_service.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.consumer.group-id=operations-ingestion-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsIngestionIT {

	private static final String DEVICE_ID = "KIOSK-TAM-003";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
			.withDatabaseName("comanche").withUsername("comanche").withPassword("test");

	@Container
	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"));

	static {
		POSTGRES.start();
		KAFKA.start();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("comanche.operations.kafka.retry.initial-backoff", () -> "10ms");
		registry.add("comanche.operations.kafka.retry.max-backoff", () -> "20ms");
	}

	@Autowired JdbcTemplate jdbc;
	@Autowired DeviceIngestionService service;
	@Autowired KafkaTemplate<Object, Object> kafka;

	@BeforeEach
	void resetDatabase() {
		jdbc.execute("TRUNCATE device_operational_state_history, device_event_observation, device_status_observation, "
				+ "device_current_metric, device_telemetry, processed_event, device_current_state, device, site, organization CASCADE");
		Instant now = Instant.parse("2026-10-08T00:00:00Z");
		Timestamp timestamp = Timestamp.from(now);
		jdbc.update("INSERT INTO organization VALUES (?, ?, ?, ?, ?)", "ORG-1", "Comanche", "ACTIVE", timestamp, timestamp);
		jdbc.update("INSERT INTO site(site_id, organization_id, name, timezone, status, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)", "SITE-TAMPINES", "ORG-1", "Tampines", "Asia/Singapore", "ACTIVE", timestamp, timestamp);
		jdbc.update("INSERT INTO device(device_id, site_id, device_type, display_name, lifecycle_status, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)", DEVICE_ID, "SITE-TAMPINES", "KIOSK", "Kiosk 3", "REGISTERED", timestamp, timestamp);
	}

	@Test
	void processesDuplicateTelemetryOnceAndKeepsNewerCurrentMetric() throws Exception {
		String newer = event("evt-new", "msg-new", "DEVICE_TELEMETRY_RECEIVED", "2026-10-08T04:00:00Z",
				"{\"metrics\":{\"printer.temperature\":72.0,\"mode\":\"READY\"}}");
		kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, newer).get();
		kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, newer).get();
		await(() -> count("device_telemetry") == 1);

		String stale = event("evt-old", "msg-old", "DEVICE_TELEMETRY_RECEIVED", "2026-10-08T03:00:00Z",
				"{\"metrics\":{\"printer.temperature\":55.0}}");
		kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, stale).get();
		await(() -> count("device_telemetry") == 2);

		BigDecimalAssert.assertValue(jdbc.queryForObject("SELECT numeric_value FROM device_current_metric "
				+ "WHERE device_id=? AND metric_name='printer.temperature'", java.math.BigDecimal.class, DEVICE_ID), "72.0");
		assertThat(count("processed_event")).isEqualTo(2);
	}

	@Test
	void persistsStatusAndLateOperationalHistoryWithoutRegressingProjection() {
		Instant received = Instant.parse("2026-10-08T05:00:00Z");
		service.process(KafkaEventValidator.STATUS_TOPIC,
				event("evt-status", "msg-status", "DEVICE_STATUS_RECEIVED", "2026-10-08T04:00:00Z",
						"{\"connectionStatus\":\"ONLINE\",\"applicationStatus\":\"RUNNING\"}"), received);
		service.process(KafkaEventValidator.DEVICE_EVENTS_TOPIC,
				event("evt-state-new", "msg-state-new", "DEVICE_EVENT_RECEIVED", "2026-10-08T04:10:00Z",
						"{\"eventType\":\"OPERATIONAL_STATE_CHANGED\",\"previousState\":\"IDLE\",\"newState\":\"TAKING_ORDER\"}"), received);
		service.process(KafkaEventValidator.DEVICE_EVENTS_TOPIC,
				event("evt-state-old", "msg-state-old", "DEVICE_EVENT_RECEIVED", "2026-10-08T04:05:00Z",
						"{\"eventType\":\"OPERATIONAL_STATE_CHANGED\",\"previousState\":\"OFFLINE\",\"newState\":\"IDLE\"}"), received);

		assertThat(count("device_status_observation")).isEqualTo(1);
		assertThat(count("device_operational_state_history")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT operational_state FROM device_current_state WHERE device_id=?",
				String.class, DEVICE_ID)).isEqualTo("TAKING_ORDER");
	}

	@Test
	void distinguishesExactDuplicatesFromIdentityConflicts() {
		String original = event("evt-identity", "msg-identity", "DEVICE_TELEMETRY_RECEIVED", "2026-10-08T04:00:00Z",
				"{\"metrics\":{\"temperature\":72.0}}");
		assertThat(service.process(KafkaEventValidator.TELEMETRY_TOPIC, original, Instant.now()))
				.isEqualTo(DeviceIngestionService.Result.PROCESSED);
		assertThat(service.process(KafkaEventValidator.TELEMETRY_TOPIC, original, Instant.now()))
				.isEqualTo(DeviceIngestionService.Result.DUPLICATE);
		String conflict = original.replace("72.0", "73.0");
		assertThatThrownBy(() -> service.process(KafkaEventValidator.TELEMETRY_TOPIC, conflict, Instant.now()))
				.isInstanceOf(EventIdentityConflictException.class);
		String logicalConflict = original.replace("evt-identity", "evt-other");
		assertThatThrownBy(() -> service.process(KafkaEventValidator.TELEMETRY_TOPIC, logicalConflict, Instant.now()))
				.isInstanceOf(EventIdentityConflictException.class);
		assertThat(count("device_telemetry")).isEqualTo(1);
	}

	@ParameterizedTest(name = "telemetry identity conflicts with {0}")
	@MethodSource("crossCategoryCases")
	void process_whenSourceIdentityIsReusedAcrossCategories_rejectsWithoutSideEffects(
			String category, String conflictTopic, String conflictType, String conflictPayload,
			String observationTable, String deadLetterTopic) {
		String original = event("evt-cross-original", "msg-cross-category", "DEVICE_TELEMETRY_RECEIVED",
				"2026-10-08T04:00:00Z", "{\"metrics\":{\"temperature\":72.0}}");
		String conflict = event("evt-cross-conflict", "msg-cross-category", conflictType,
				"2026-10-08T04:01:00Z", conflictPayload);

		assertThat(service.process(KafkaEventValidator.TELEMETRY_TOPIC, original, Instant.now()))
				.isEqualTo(DeviceIngestionService.Result.PROCESSED);
		assertThat(service.process(KafkaEventValidator.TELEMETRY_TOPIC, original, Instant.now()))
				.isEqualTo(DeviceIngestionService.Result.DUPLICATE);
		assertThatThrownBy(() -> service.process(conflictTopic, conflict, Instant.now()))
				.isInstanceOf(EventIdentityConflictException.class);

		assertThat(count("processed_event")).isEqualTo(1);
		assertThat(count("device_telemetry")).isEqualTo(1);
		assertThat(count(observationTable)).isZero();
		assertThat(jdbc.queryForObject("SELECT operational_state FROM device_current_state WHERE device_id=?",
				String.class, DEVICE_ID)).isEqualTo("IDLE");
		assertThat(jdbc.queryForObject("SELECT reported_status::text FROM device_current_state WHERE device_id=?",
				String.class, DEVICE_ID)).isEqualTo("{}");
	}

	@ParameterizedTest(name = "Kafka routes telemetry/{0} identity conflict to {5}")
	@MethodSource("crossCategoryCases")
	void consume_whenSourceIdentityIsReusedAcrossCategories_routesConflictToCategoryDeadLetterTopic(
			String category, String conflictTopic, String conflictType, String conflictPayload,
			String observationTable, String deadLetterTopic) throws Exception {
		String identity = "msg-kafka-cross-" + category;
		String original = event("evt-kafka-original-" + category, identity, "DEVICE_TELEMETRY_RECEIVED",
				"2026-10-08T04:00:00Z", "{\"metrics\":{\"temperature\":72.0}}");
		String conflict = event("evt-kafka-conflict-" + category, identity, conflictType,
				"2026-10-08T04:01:00Z", conflictPayload);

		try (KafkaConsumer<String, String> consumer = dltConsumer()) {
			consumer.subscribe(List.of(deadLetterTopic));
			consumer.poll(Duration.ofMillis(100));
			kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, original).get();
			kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, original).get();
			await(() -> count("device_telemetry") == 1);
			kafka.send(conflictTopic, DEVICE_ID, conflict).get();

			ConsumerRecord<String, String> deadLetter = pollMatching(consumer, conflict);
			assertThat(deadLetter.topic()).isEqualTo(deadLetterTopic);
			assertThat(deadLetter.value()).isEqualTo(conflict);
			assertThat(headerText(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(conflictTopic);
			assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
			assertThat(headerText(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
					.contains("Event identity conflicts");
		}

		assertThat(count("processed_event")).isEqualTo(1);
		assertThat(count("device_telemetry")).isEqualTo(1);
		assertThat(count(observationTable)).isZero();
		assertThat(jdbc.queryForObject("SELECT operational_state FROM device_current_state WHERE device_id=?",
				String.class, DEVICE_ID)).isEqualTo("IDLE");
		assertThat(jdbc.queryForObject("SELECT reported_status::text FROM device_current_state WHERE device_id=?",
				String.class, DEVICE_ID)).isEqualTo("{}");
	}

	@Test
	void publishesInvalidMessagesToVersionedDeadLetterTopicWithDiagnostics() throws Exception {
		try (KafkaConsumer<String, String> consumer = dltConsumer()) {
			consumer.subscribe(List.of("comanche.device.telemetry.dlt.v1"));
			consumer.poll(Duration.ofMillis(100));
			String malformed = "{\"eventId\":\"bad-event\"}";
			kafka.send(KafkaEventValidator.TELEMETRY_TOPIC, DEVICE_ID, malformed).get();
			ConsumerRecord<String, String> deadLetter = pollMatching(consumer, malformed);
			assertThat(deadLetter.value()).isEqualTo(malformed);
			assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
			assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
			assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotNull();
		}
	}

	private KafkaConsumer<String, String> dltConsumer() {
		return new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "dlt-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
	}

	private static ConsumerRecord<String, String> pollMatching(KafkaConsumer<String, String> consumer, String expectedValue) {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		do {
			var records = consumer.poll(Duration.ofMillis(250));
			for (ConsumerRecord<String, String> record : records) {
				if (expectedValue.equals(record.value())) return record;
			}
		} while (System.nanoTime() < deadline);
		throw new AssertionError("Expected dead-letter record was not received");
	}

	private static String headerText(ConsumerRecord<String, String> record, String name) {
		var header = record.headers().lastHeader(name);
		assertThat(header).as("Kafka header %s", name).isNotNull();
		return new String(header.value(), StandardCharsets.UTF_8);
	}

	private static Stream<Arguments> crossCategoryCases() {
		return Stream.of(
				Arguments.of("status", KafkaEventValidator.STATUS_TOPIC, "DEVICE_STATUS_RECEIVED",
						"{\"connectionStatus\":\"ONLINE\",\"applicationStatus\":\"RUNNING\"}",
						"device_status_observation", "comanche.device.status.dlt.v1"),
				Arguments.of("device-event", KafkaEventValidator.DEVICE_EVENTS_TOPIC, "DEVICE_EVENT_RECEIVED",
						"{\"eventType\":\"OPERATIONAL_STATE_CHANGED\",\"previousState\":\"IDLE\",\"newState\":\"TAKING_ORDER\"}",
						"device_event_observation", "comanche.device.events.dlt.v1"));
	}

	private long count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(50);
		}
		throw new AssertionError("Condition was not satisfied");
	}

	private static String event(String eventId, String messageId, String type, String occurredAt, String payload) {
		return """
				{"eventId":"%s","eventType":"%s","schemaVersion":"1.0","occurredAt":"%s",
				 "publishedAt":"2026-10-08T04:20:00Z","source":"comanche-iot-gateway",
				 "aggregateId":"%s","correlationId":"%s","sourceMessageId":"%s","deviceId":"%s","payload":%s}
				""".formatted(eventId, type, occurredAt, DEVICE_ID, messageId, messageId, DEVICE_ID, payload);
	}

	private static final class BigDecimalAssert {
		static void assertValue(java.math.BigDecimal actual, String expected) {
			assertThat(actual).isEqualByComparingTo(expected);
		}
	}
}
