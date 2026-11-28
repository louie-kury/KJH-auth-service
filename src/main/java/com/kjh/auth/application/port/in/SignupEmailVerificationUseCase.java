package com.kjh.auth.application.port.in;

import com.kjh.auth.domain.model.Email;

public interface SignupEmailVerificationUseCase {
    void sendCode(Email email, String clientIp);
    void countVerificationRequest(String clientIp);
    String verifyCode(Email email, String code);
}
