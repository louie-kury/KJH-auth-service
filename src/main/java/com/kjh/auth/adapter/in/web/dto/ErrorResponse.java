package com.kjh.auth.adapter.in.web.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int code, String errorCode, String message, List<FieldError> details) {}
