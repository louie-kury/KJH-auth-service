package com.kjh.auth.application.port.out;

import com.kjh.auth.domain.model.Email;

public interface VerificationEmailSender {

    void send(Email email, String code);
}
