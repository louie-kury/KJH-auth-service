package com.kjh.auth.adapter.in.web;

import com.kjh.auth.adapter.in.web.dto.ErrorResponse;
import com.kjh.auth.adapter.in.web.dto.FieldError;
import com.kjh.auth.application.exception.EmailVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> invalidInput(ValidationException exception) {
        return error(EmailVerificationErrorCode.VALIDATION_FAILED, exception.details, 0);
    }

    @ExceptionHandler(UnsupportedBodyException.class)
    public ResponseEntity<ErrorResponse> unsupportedBody() {
        return error(EmailVerificationErrorCode.UNSUPPORTED_MEDIA_TYPE, null, 0);
    }

    @ExceptionHandler(UntrustedSourceException.class)
    public ResponseEntity<ErrorResponse> untrustedSource() {
        return error(EmailVerificationErrorCode.REQUEST_SOURCE_UNTRUSTED, null, 0);
    }

    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<ErrorResponse> verificationError(EmailVerificationException exception) {
        return error(EmailVerificationErrorCode.from(exception.error()), null, exception.retryAfterSeconds());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse response) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(
                    new ErrorResponse(response.getStatusCode().value(), "HTTP_ERROR", "요청을 처리할 수 없습니다.", null));
        }
        LOGGER.error("Unexpected request processing failure ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse(500, "INTERNAL_SERVER_ERROR", "일시적으로 요청을 처리할 수 없습니다.", null));
    }

    private static ResponseEntity<ErrorResponse> error(EmailVerificationErrorCode error,
            List<FieldError> details, long retryAfterSeconds) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status());
        if (error.status() == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return response.body(new ErrorResponse(error.status().value(), error.name(), error.message(), details));
    }
}
