package com.kjh.auth.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.VerificationEmailSender;
import com.kjh.auth.domain.model.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;


import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfEnvironmentVariable(named = "AUTH006_INTEGRATION_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "auth.signup.email.trusted-gateway=127.0.0.1",
        "auth.signup.email.from=auth@example.test",
        "auth.signup.email.hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class EmailVerificationIntegrationTests {

    @LocalServerPort
    private int port;


    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private VerificationEmailSender mail;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String keyPrefix = "auth006-test:" + UUID.randomUUID() + ":";

    @BeforeAll
    void isolateRedisKeysAndCheckVersion() {
        assertThat(redis.getConnectionFactory().getConnection().serverCommands()
                .info("server").getProperty("redis_version")).isEqualTo("8.2.11");
        redis.setKeySerializer(new StringRedisSerializer() {
            @Override
            public byte[] serialize(String value) {
                return super.serialize(keyPrefix + value);
            }
        });
    }


    @Test
    void normalizesEmailAndEmitsOnlyOneProofForConcurrentVerification() throws Exception {
        String localPart = "User.Name+" + UUID.randomUUID();
        String canonical = localPart + "@example.test";
        String ip = "192.0.2.10";
        HttpResponse<String> accepted = send("code", emailBody(" " + localPart + "@EXAMPLE.TEST "), ip);

        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<Integer>read(accepted.body(), "$.code")).isEqualTo(202);
        assertThat(JsonPath.<Object>read(accepted.body(), "$.data")).isNull();
        String code = deliveredCode(canonical);

        HttpRequest request = request("verify", verificationBody(canonical, code), ip);
        var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> a = first.join();
        HttpResponse<String> b = second.join();

        assertThat(new int[]{a.statusCode(), b.statusCode()}).containsExactlyInAnyOrder(200, 400);
        HttpResponse<String> success = a.statusCode() == 200 ? a : b;
        assertError(a.statusCode() == 400 ? a : b, 400, "VERIFICATION_CODE_UNAVAILABLE");

        String proof = JsonPath.read(success.body(), "$.data.emailVerificationProof");
        assertThat(proof).matches("[A-Za-z0-9_-]{43}");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(proof.getBytes(StandardCharsets.US_ASCII));
        String proofKey = "auth:{signup-email}:proof:" + HexFormat.of().formatHex(digest);
        assertThat(redis.opsForValue().get(proofKey)).isEqualTo("CAREGIVER_SIGNUP\n" + canonical);
        assertThat(redis.getExpire(proofKey, TimeUnit.SECONDS)).isBetween(540L, 600L);
    }

    @Test
    void rejectsTheCorrectCodeAfterFiveMismatches() throws Exception {
        String email = freshEmail();
        String ip = "192.0.2.20";
        assertThat(send("code", emailBody(email), ip).statusCode()).isEqualTo(202);
        String code = deliveredCode(email);
        String wrongCode = code.equals("000000") ? "999999" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertError(send("verify", verificationBody(email, wrongCode), ip), 400, "VERIFICATION_CODE_MISMATCH");
        }
        assertError(send("verify", verificationBody(email, code), ip), 400, "VERIFICATION_CODE_UNAVAILABLE");
    }

    @Test
    void rejectsTheCodeAfterItsRedisExpiration() throws Exception {
        String email = freshEmail();
        String ip = "192.0.2.25";
        assertThat(send("code", emailBody(email), ip).statusCode()).isEqualTo(202);
        String code = deliveredCode(email);
        String activeCode = redisEmailKey("code", email);

        assertThat(redis.getExpire(activeCode, TimeUnit.SECONDS)).isBetween(240L, 300L);
        assertThat(redis.expire(activeCode, Duration.ZERO)).isTrue();
        assertError(send("verify", verificationBody(email, code), ip), 400, "VERIFICATION_CODE_UNAVAILABLE");
    }

    @Test
    void limitsImmediateResendAcrossClientAddresses() throws Exception {
        String email = freshEmail();
        assertThat(send("code", emailBody(email), "192.0.2.30").statusCode()).isEqualTo(202);
        HttpResponse<String> denied = send("code", emailBody(email), "192.0.2.31");

        assertError(denied, 429, "EMAIL_RESEND_COOLDOWN");
        assertThat(denied.headers().firstValue("Retry-After").orElseThrow()).matches("[1-9]\\d*");
        deliveredCode(email);
    }

    @Test
    void limitsOneClientAddressAcrossDifferentEmails() throws Exception {
        String ip = "2001:db8:" + UUID.randomUUID().toString().substring(0, 4) + "::1";

        for (int attempt = 0; attempt < 30; attempt++) {
            assertThat(send("code", emailBody(freshEmail()), ip).statusCode()).isEqualTo(202);
        }
        HttpResponse<String> denied = send("code", emailBody(freshEmail()), ip);

        assertError(denied, 429, "RATE_LIMITED");
        assertThat(Integer.parseInt(denied.headers().firstValue("Retry-After").orElseThrow()))
                .isBetween(3500, 3600);
    }

    @Test
    void explainsHourlyEmailLimitEvenDuringCooldown() throws Exception {
        String email = freshEmail();
        assertThat(send("code", emailBody(email), "192.0.2.32").statusCode()).isEqualTo(202);
        String history = redisEmailKey("send:email", email);
        long now = System.currentTimeMillis();
        for (int attempt = 0; attempt < 4; attempt++) {
            redis.opsForZSet().add(history, UUID.randomUUID().toString(), now - 1000);
        }
        var response = send("code", emailBody(email), "192.0.2.33");
        assertError(response, 429, "EMAIL_SEND_LIMIT_EXCEEDED");
        assertThat(JsonPath.<String>read(response.body(), "$.message"))
                .contains("1시간", "5회");
        assertThat(Integer.parseInt(response.headers().firstValue("Retry-After").orElseThrow()))
                .isBetween(3500, 3600);
        deliveredCode(email);
    }

    @Test
    void rejectsUntrustedHeadersAndInvalidPayloadsWithoutSendingMail() throws Exception {
        assertError(send("code", emailBody(freshEmail()), null), 403, "REQUEST_SOURCE_UNTRUSTED");
        assertError(send("code", emailBody("bad address@example.test"), "192.0.2.40"), 400, "VALIDATION_FAILED");
        assertError(send("verify", verificationBody(freshEmail(), "12345"), "192.0.2.40"), 400, "VALIDATION_FAILED");
        verifyNoInteractions(mail);
    }

    @Test
    void countsMalformedVerificationBodiesAgainstTheClientAddress() throws Exception {
        String ip = "2001:db8:1:" + UUID.randomUUID().toString().substring(0, 4) + "::1";

        for (int attempt = 0; attempt < 60; attempt++) {
            assertError(send("verify", "{", ip), 400, "VALIDATION_FAILED");
        }
        HttpResponse<String> denied = send("verify", "{", ip);

        assertError(denied, 429, "RATE_LIMITED");
        assertThat(denied.headers().firstValue("Retry-After").orElseThrow()).matches("[1-9]\\d*");
    }

    @Test
    void providerResendFailureKeepsPriorActiveCodeAndChargesRateLimit() throws Exception {
        String email = freshEmail();
        String ip = "192.0.2.50";
        assertThat(send("code", emailBody(email), ip).statusCode()).isEqualTo(202);
        String originalCode = deliveredCode(email);
        assertThat(redis.expire(redisEmailKey("cooldown", email), Duration.ZERO)).isTrue();
        doThrow(new EmailVerificationException(Error.SERVICE_UNAVAILABLE)).when(mail).send(any(Email.class), anyString());

        assertError(send("code", emailBody(email), ip), 503, "SERVICE_UNAVAILABLE");
        assertError(send("code", emailBody(email), ip), 429, "EMAIL_RESEND_COOLDOWN");
        assertThat(send("verify", verificationBody(email, originalCode), ip).statusCode()).isEqualTo(200);
    }

    private String deliveredCode(String email) {
        ArgumentCaptor<Email> recipient = ArgumentCaptor.forClass(Email.class);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail).send(recipient.capture(), code.capture());
        assertThat(recipient.getValue().value()).isEqualTo(email);
        assertThat(code.getValue()).matches("[0-9]{6}");
        return code.getValue();
    }

    private HttpResponse<String> send(String action, String body, String ip) throws IOException, InterruptedException {
        return http.send(request(action, body, ip), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String action, String body, String ip) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/signup/email/" + action))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (ip != null) {
            request.header("X-Client-IP", ip);
        }
        return request.build();
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.errorCode")).isEqualTo(code);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isNotBlank();
    }

    private String freshEmail() {
        String email = UUID.randomUUID() + "@example.test";
        return email;
    }

    private static String redisEmailKey(String group, String email) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.US_ASCII));
        return "auth:{signup-email}:" + group + ":" + HexFormat.of().formatHex(digest);
    }

    private static String emailBody(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String verificationBody(String email, String code) {
        return "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code);
    }
}
