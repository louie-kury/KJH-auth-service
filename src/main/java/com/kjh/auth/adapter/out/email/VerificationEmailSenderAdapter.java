package com.kjh.auth.adapter.out.email;

import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.VerificationEmailSender;
import com.kjh.auth.domain.model.Email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

public final class VerificationEmailSenderAdapter implements VerificationEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationEmailSenderAdapter.class);

    private final RestClient client;
    private final String from;
    private final String apiKey;

    public VerificationEmailSenderAdapter(RestClient client, String from, String apiKey) {
        this.client = client;
        this.from = new Email(from).value();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Configure the Brevo API key");
        }
        this.apiKey = apiKey;
    }

    @Override
    public void send(Email email, String code) {
        String text = """
                보호자 가입 인증번호입니다.

                %s

                인증번호는 5분간 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code);
        try {
            var response = client.post().uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender", Map.of("email", from),
                            "to", List.of(Map.of("email", email.value())),
                            "subject", "보호자 가입 이메일 인증번호", "textContent", text))
                    .retrieve().toEntity(SendResponse.class);
            SendResponse body = response.getBody();
            if (response.getStatusCode() != HttpStatus.CREATED || body == null
                    || body.messageId() == null || body.messageId().isBlank()) {
                LOGGER.warn("Signup verification Brevo response did not confirm acceptance");
                throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
            }
        } catch (RestClientException exception) {
            LOGGER.warn("Signup verification Brevo HTTP send failed ({})", exception.getClass().getSimpleName());
            throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
        }
    }

    public record SendResponse(String messageId) {
    }
}
