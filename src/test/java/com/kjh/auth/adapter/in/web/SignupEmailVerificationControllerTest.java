package com.kjh.auth.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.in.SignupEmailVerificationUseCase;
import com.kjh.auth.domain.model.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SignupEmailVerificationControllerTest {
    private SignupEmailVerificationUseCase service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SignupEmailVerificationUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new SignupEmailVerificationController(
                service, JsonMapper.builder().build(), ""))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void getRequestsPreserveMethodNotAllowedAndAllowHeader() throws Exception {
        for (String action : List.of("code", "verify")) {
            var response = mvc.perform(get("/api/v1/auth/signup/email/" + action))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
        }
    }

    @Test
    void wrapsSendAndVerificationSuccess() throws Exception {
        var sent = mvc.perform(post("/api/v1/auth/signup/email/code")
                .contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                .andReturn().getResponse();
        assertThat(sent.getStatus()).isEqualTo(202);
        assertThat(JsonPath.<Integer>read(sent.getContentAsString(), "$.code")).isEqualTo(202);
        assertThat(JsonPath.<Object>read(sent.getContentAsString(), "$.data")).isNull();
        when(service.verifyCode(new Email("user@example.com"), "012345")).thenReturn("proof");
        var verified = mvc.perform(post("/api/v1/auth/signup/email/verify")
                .contentType("application/json")
                .content("{\"email\":\"user@example.com\",\"code\":\"012345\"}"))
                .andReturn().getResponse();
        assertThat(verified.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(verified.getContentAsString(), "$.code")).isEqualTo(200);
        assertThat(JsonPath.<String>read(verified.getContentAsString(), "$.data.emailVerificationProof"))
                .isEqualTo("proof");
        assertThat(verified.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void collectsAllInvalidFieldsWithoutEchoingValues() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/signup/email/verify")
                .contentType("application/json").content("{\"email\":\"bad\",\"code\":\"secret\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.errorCode"))
                .isEqualTo("VALIDATION_FAILED");
        List<Map<String, Object>> details = JsonPath.read(response.getContentAsString(), "$.details");
        assertThat(details).extracting(detail -> detail.get("field")).containsExactlyInAnyOrder("email", "code");
        assertThat(response.getContentAsString()).doesNotContain("rejectedValue", "secret");
        verify(service, never()).verifyCode(any(), anyString());
    }

    @Test
    void includesDetailsForASingleFieldButNotMalformedJson() throws Exception {
        var fieldError = mvc.perform(post("/api/v1/auth/signup/email/code")
                .contentType("application/json").content("{}" )).andReturn().getResponse();
        assertThat(JsonPath.<List<?>>read(fieldError.getContentAsString(), "$.details")).hasSize(1);
        var malformed = mvc.perform(post("/api/v1/auth/signup/email/verify")
                .contentType("application/json").content("{" )).andReturn().getResponse();
        assertThat(malformed.getStatus()).isEqualTo(400);
        assertThat(malformed.getContentAsString()).doesNotContain("details");
        verify(service).countVerificationRequest(anyString());
    }

    @Test
    void returnsBusinessErrorsAndRetryHeader() throws Exception {
        doThrow(new EmailVerificationException(Error.RATE_LIMITED, 60)).when(service).sendCode(any(), anyString());
        var response = mvc.perform(post("/api/v1/auth/signup/email/code")
                .contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(JsonPath.<Integer>read(response.getContentAsString(), "$.code")).isEqualTo(429);
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.errorCode")).isEqualTo("RATE_LIMITED");
        assertThat(response.getContentAsString()).doesNotContain("details");
    }

    @Test
    void returnsConsistentUnsupportedMediaTypeErrors() throws Exception {
        for (String action : List.of("code", "verify")) {
            var response = mvc.perform(post("/api/v1/auth/signup/email/" + action)
                    .contentType("text/plain").content("input")).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(415);
            assertThat(JsonPath.<Integer>read(response.getContentAsString(), "$.code")).isEqualTo(415);
            assertThat(JsonPath.<String>read(response.getContentAsString(), "$.errorCode"))
                    .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        }
    }

    @Test
    void unexpectedFailureReturnsSafeCommonError() throws Exception {
        doThrow(new IllegalStateException("sensitive internal detail"))
                .when(service).sendCode(any(), anyString());
        var response = mvc.perform(post("/api/v1/auth/signup/email/code")
                .contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(JsonPath.<Integer>read(response.getContentAsString(), "$.code")).isEqualTo(500);
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.errorCode")).isNotBlank();
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.message")).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain("sensitive internal detail", "IllegalStateException");
    }

    @Test
    void explainsEachEmailSendPolicyAndPreservesRetryAfter() throws Exception {
        for (Error error : List.of(Error.EMAIL_RESEND_COOLDOWN, Error.EMAIL_SEND_LIMIT_EXCEEDED)) {
            doThrow(new EmailVerificationException(error, 60)).when(service).sendCode(any(), anyString());
            var response = mvc.perform(post("/api/v1/auth/signup/email/code")
                    .contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            assertThat(JsonPath.<String>read(response.getContentAsString(), "$.errorCode"))
                    .isEqualTo(error.name());
            String expectedMessage = error == Error.EMAIL_RESEND_COOLDOWN
                    ? "인증번호는 60초 간격으로 요청할 수 있습니다."
                    : "이메일별 인증번호 발송은 최근 1시간에 최대 5회까지 가능합니다.";
            assertThat(JsonPath.<String>read(response.getContentAsString(), "$.message"))
                    .isEqualTo(expectedMessage);
        }
    }
}
