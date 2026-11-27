package com.kjh.auth.application.exception;

public final class EmailVerificationException extends RuntimeException {

    public enum Error {
        CODE_MISMATCH,
        CODE_UNAVAILABLE,
        EMAIL_RESEND_COOLDOWN,
        EMAIL_SEND_LIMIT_EXCEEDED,
        RATE_LIMITED,
        SERVICE_UNAVAILABLE
    }

    private final Error error;
    private final long retryAfterSeconds;

    public EmailVerificationException(Error error) {
        this(error, 0);
    }

    public EmailVerificationException(Error error, long retryAfterSeconds) {
        super(error.name());
        this.error = error;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Error error() {
        return error;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
