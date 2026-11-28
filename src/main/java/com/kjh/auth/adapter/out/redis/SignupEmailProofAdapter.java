package com.kjh.auth.adapter.out.redis;

import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.SignupEmailProofStore;
import com.kjh.auth.domain.model.Email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HexFormat;

public final class SignupEmailProofAdapter implements SignupEmailProofStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignupEmailProofAdapter.class);
    private static final String KEY_PREFIX = "auth:{signup-email}:proof:";
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public SignupEmailProofAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(Email email, byte[] proofHash) {
        String key = KEY_PREFIX + HexFormat.of().formatHex(proofHash);
        String value = "CAREGIVER_SIGNUP\n" + email.value();
        try {
            redis.opsForValue().set(key, value, LIFETIME);
        } catch (DataAccessException exception) {
            LOGGER.warn("Signup email proof Redis save failed ({})", exception.getClass().getSimpleName());
            throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
        }
    }
}
