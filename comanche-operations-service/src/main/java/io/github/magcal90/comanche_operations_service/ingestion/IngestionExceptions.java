package io.github.magcal90.comanche_operations_service.ingestion;

final class InvalidEventException extends RuntimeException {
	InvalidEventException(String message) { super(message); }
	InvalidEventException(String message, Throwable cause) { super(message, cause); }
}

final class UnknownDeviceException extends RuntimeException {
	UnknownDeviceException(String deviceId) { super("Unknown device: " + deviceId); }
}

final class EventIdentityConflictException extends RuntimeException {
	EventIdentityConflictException(String message) { super(message); }
}
