package com.kjh.auth.adapter.in.web;
import com.kjh.auth.adapter.in.web.dto.FieldError;
import java.util.List;
final class ValidationException extends RuntimeException {
    final List<FieldError> details;
    ValidationException(List<FieldError> details) { this.details = details; }
}
