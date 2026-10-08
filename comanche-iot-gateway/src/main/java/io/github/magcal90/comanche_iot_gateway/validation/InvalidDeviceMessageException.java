package io.github.magcal90.comanche_iot_gateway.validation;

public final class InvalidDeviceMessageException extends RuntimeException {

	private final RejectionReason reason;

	public InvalidDeviceMessageException(RejectionReason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public InvalidDeviceMessageException(RejectionReason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public RejectionReason reason() {
		return reason;
	}
}
