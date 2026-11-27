package com.kjh.auth.adapter.in.web;

import com.kjh.auth.application.exception.EmailVerificationException;

import org.springframework.http.HttpStatus;

public enum EmailVerificationErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type은 application/json이어야 합니다."),
    REQUEST_SOURCE_UNTRUSTED(HttpStatus.FORBIDDEN, "요청 출처를 확인할 수 없습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    VERIFICATION_CODE_UNAVAILABLE(HttpStatus.BAD_REQUEST,
            "사용할 수 없는 인증번호입니다. 인증번호를 다시 요청해 주세요."),
    EMAIL_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 60초 간격으로 요청할 수 있습니다."),
    EMAIL_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "이메일별 인증번호 발송은 최근 1시간에 최대 5회까지 가능합니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    public static final String EMAIL_TYPE_MESSAGE = "이메일을 문자열로 입력해 주세요.";
    public static final String EMAIL_FORMAT_MESSAGE = "이메일 형식이 올바르지 않습니다.";
    public static final String CODE_FORMAT_MESSAGE = "인증번호는 숫자 6자리 문자열로 입력해 주세요.";

    private final HttpStatus status;
    private final String message;

    EmailVerificationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public static EmailVerificationErrorCode from(EmailVerificationException.Error error) {
        return switch (error) {
            case CODE_MISMATCH -> VERIFICATION_CODE_MISMATCH;
            case CODE_UNAVAILABLE -> VERIFICATION_CODE_UNAVAILABLE;
            case EMAIL_RESEND_COOLDOWN -> EMAIL_RESEND_COOLDOWN;
            case EMAIL_SEND_LIMIT_EXCEEDED -> EMAIL_SEND_LIMIT_EXCEEDED;
            case RATE_LIMITED -> RATE_LIMITED;
            case SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE;
        };
    }
}
