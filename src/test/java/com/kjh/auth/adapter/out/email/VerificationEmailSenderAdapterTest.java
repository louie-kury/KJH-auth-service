package com.kjh.auth.adapter.out.email;

import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.domain.model.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class VerificationEmailSenderAdapterTest {

    private MockRestServiceServer server;
    private VerificationEmailSenderAdapter sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder client = RestClient.builder().baseUrl("https://api.brevo.com");
        server = MockRestServiceServer.bindTo(client).build();
        sender = new VerificationEmailSenderAdapter(client.build(), "auth@example.test", "test-api-key");
    }

    @Test
    void sendsTheRecipientAndCodeUsingTheBrevoHttpContract() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("auth@example.test"))
                .andExpect(jsonPath("$.to[0].email").value("user@example.test"))
                .andExpect(jsonPath("$.subject").value("보호자 가입 이메일 인증번호"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<test-message@brevo.test>\"}"));

        sender.send(new Email("user@example.test"), "123456");

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void mapsProviderFailuresToTheServiceUnavailableContract(int status) {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"provider_error\",\"message\":\"synthetic failure\"}"));

        assertUnavailable();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"messageId\":\" \"}", "{", ""})
    void rejectsAnUnconfirmedAcceptance(String body) {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body));

        assertUnavailable();
        server.verify();
    }

    @Test
    void rejectsUnexpectedSuccessfulStatus() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"test-message\"}"));

        assertUnavailable();
        server.verify();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> sender.send(new Email("user@example.test"), "123456"))
                .isInstanceOfSatisfying(EmailVerificationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(Error.SERVICE_UNAVAILABLE));
    }
}
