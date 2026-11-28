package com.kjh.auth.adapter.out.redis;

import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.VerificationCodeStore;
import com.kjh.auth.domain.model.Email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class EmailVerificationCodeAdapter implements VerificationCodeStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationCodeAdapter.class);
    private static final String PREFIX = "auth:{signup-email}:";
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RESERVE = RedisScript.of(
            new ClassPathResource("redis/reserve-email-code.lua"), List.class);
    private static final RedisScript<Long> COMPLETE = script("complete-email-code");
    private static final RedisScript<Long> VERIFY = script("verify-email-code");
    private static final RedisScript<Long> COUNT_VERIFICATION = script("count-verification-request");

    private final StringRedisTemplate redis;
    private final String sendsPerMinute;

    public EmailVerificationCodeAdapter(StringRedisTemplate redis, int sendsPerMinute) {
        this.redis = redis;
        if (sendsPerMinute < 1) {
            throw new IllegalArgumentException("The email send limit must be positive");
        }
        this.sendsPerMinute = Integer.toString(sendsPerMinute);
    }

    @Override
    public long reserve(Email email, String clientIp, String issuanceId, String digest) {
        String emailId = emailId(email);
        List<?> result = execute(RESERVE, List.of(
                PREFIX + "pending:" + emailId, PREFIX + "cooldown:" + emailId,
                PREFIX + "send:email:" + emailId, PREFIX + "send:ip:" + clientIp,
                PREFIX + "send:global", PREFIX + "code:" + emailId), issuanceId, sendsPerMinute, digest);
        if (result.size() != 2 || !(result.get(0) instanceof Number wait)) {
            LOGGER.warn("Signup email verification Redis reservation returned an invalid result");
            throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
        }
        long retryAfterSeconds = wait.longValue();
        if (retryAfterSeconds > 0) {
            Error error = switch (String.valueOf(result.get(1))) {
                case "EMAIL_RESEND_COOLDOWN" -> Error.EMAIL_RESEND_COOLDOWN;
                case "EMAIL_SEND_LIMIT_EXCEEDED" -> Error.EMAIL_SEND_LIMIT_EXCEEDED;
                case "RATE_LIMITED" -> Error.RATE_LIMITED;
                default -> Error.SERVICE_UNAVAILABLE;
            };
            throw new EmailVerificationException(error, retryAfterSeconds);
        }
        return retryAfterSeconds;
    }

    @Override
    public boolean activate(Email email, String issuanceId, String digest) {
        boolean activated = complete(email, issuanceId, digest);
        if (!activated) {
            LOGGER.warn("Signup email verification reservation expired before activation");
        }
        return activated;
    }

    @Override
    public void cancel(Email email, String issuanceId) {
        complete(email, issuanceId, "");
    }

    @Override
    public long countVerificationRequest(String clientIp, String attemptId) {
        return execute(COUNT_VERIFICATION, List.of(PREFIX + "verify:ip:" + clientIp), attemptId);
    }

    @Override
    public long verify(Email email, String attemptId, String digest) {
        String emailId = emailId(email);
        return execute(VERIFY, List.of(PREFIX + "code:" + emailId,
                PREFIX + "verify:email:" + emailId), attemptId, digest);
    }

    private boolean complete(Email email, String issuanceId, String digest) {
        String emailId = emailId(email);
        return execute(COMPLETE, List.of(PREFIX + "pending:" + emailId, PREFIX + "code:" + emailId),
                issuanceId, digest) == 1;
    }

    private <T> T execute(RedisScript<T> script, List<String> keys, String... args) {
        try {
            T result = redis.execute(script, keys, (Object[]) args);
            if (result != null) {
                return result;
            }
            LOGGER.warn("Signup email verification Redis operation returned no result");
        } catch (DataAccessException exception) {
            LOGGER.warn("Signup email verification Redis operation failed ({})", exception.getClass().getSimpleName());
        }
        throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
    }

    private static RedisScript<Long> script(String name) {
        return RedisScript.of(new ClassPathResource("redis/" + name + ".lua"), Long.class);
    }

    private static String emailId(Email email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(email.value().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash email lookup key", exception);
        }
    }
}
