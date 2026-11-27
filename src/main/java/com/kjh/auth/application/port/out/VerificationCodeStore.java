package com.kjh.auth.application.port.out;

import com.kjh.auth.domain.model.Email;

public interface VerificationCodeStore {

    // Negative result requests a replacement candidate because the previous code is still active.
    long reserve(Email email, String clientIp, String issuanceId, String digest);

    boolean activate(Email email, String issuanceId, String digest);

    void cancel(Email email, String issuanceId);

    long countVerificationRequest(String clientIp, String attemptId);

    // Zero consumed the code. Negative values distinguish mismatch and unavailable.
    long verify(Email email, String attemptId, String digest);
}
