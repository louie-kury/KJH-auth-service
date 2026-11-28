package com.kjh.auth.application.service;

import com.kjh.auth.application.port.in.SignupEmailVerificationUseCase;
import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.SignupEmailProofStore;
import com.kjh.auth.application.port.out.VerificationCodeStore;
import com.kjh.auth.application.port.out.VerificationEmailSender;
import com.kjh.auth.domain.model.Email;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class SignupEmailVerificationService implements SignupEmailVerificationUseCase {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final VerificationCodeStore codes;
    private final SignupEmailProofStore proofs;
    private final VerificationEmailSender sender;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec hmacKey;

    public SignupEmailVerificationService(
            VerificationCodeStore codes, SignupEmailProofStore proofs,
            VerificationEmailSender sender, String encodedHmacKey) {
        this.codes = codes;
        this.proofs = proofs;
        this.sender = sender;
        try {
            byte[] key = Base64.getDecoder().decode(encodedHmacKey);
            if (key.length < 32) {
                throw new IllegalArgumentException();
            }
            this.hmacKey = new SecretKeySpec(key, HMAC_ALGORITHM);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Configure a Base64 email code HMAC key with 32 or more bytes");
        }
    }

    public void sendCode(Email email, String clientIp) {
        String issuanceId = UUID.randomUUID().toString();
        String code;
        String digest;
        long reservation;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            digest = codeDigest(email, code);
            reservation = codes.reserve(email, clientIp, issuanceId, digest);
        } while (reservation < 0);
        requireWithinLimit(reservation);

        try {
            sender.send(email, code);
        } catch (EmailVerificationException exception) {
            try {
                codes.cancel(email, issuanceId);
            } catch (EmailVerificationException ignored) {
                // Redis expiry releases a reservation if cleanup is unavailable.
            }
            throw exception;
        }
        if (!codes.activate(email, issuanceId, digest)) {
            throw new EmailVerificationException(Error.SERVICE_UNAVAILABLE);
        }
    }

    public void countVerificationRequest(String clientIp) {
        requireWithinLimit(codes.countVerificationRequest(clientIp, UUID.randomUUID().toString()));
    }

    public String verifyCode(Email email, String code) {
        long result = codes.verify(email, UUID.randomUUID().toString(), codeDigest(email, code));
        requireWithinLimit(result);
        if (result < 0) {
            throw new EmailVerificationException(result == -1 ? Error.CODE_MISMATCH : Error.CODE_UNAVAILABLE);
        }
        byte[] proofBytes = new byte[32];
        random.nextBytes(proofBytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes);
        proofs.save(email, proofDigest(proof));
        return proof;
    }

    private static void requireWithinLimit(long retryAfterSeconds) {
        if (retryAfterSeconds > 0) {
            throw new EmailVerificationException(Error.RATE_LIMITED, retryAfterSeconds);
        }
    }

    private String codeDigest(Email email, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            byte[] digest = mac.doFinal((email.value() + "\n" + code).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot hash email verification code", exception);
        }
    }

    private static byte[] proofDigest(String proof) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(proof.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot hash signup email proof", exception);
        }
    }
}
