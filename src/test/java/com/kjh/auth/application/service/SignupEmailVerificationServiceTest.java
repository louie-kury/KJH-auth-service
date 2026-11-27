package com.kjh.auth.application.service;

import com.kjh.auth.application.exception.EmailVerificationException;
import com.kjh.auth.application.exception.EmailVerificationException.Error;
import com.kjh.auth.application.port.out.SignupEmailProofStore;
import com.kjh.auth.application.port.out.VerificationCodeStore;
import com.kjh.auth.application.port.out.VerificationEmailSender;
import com.kjh.auth.domain.model.Email;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SignupEmailVerificationServiceTest {

    @Test
    void reportsProofStoreFailureAndRequiresANewCode() {
        Email email = new Email("user@example.test");
        VerificationCodeStore codes = mock(VerificationCodeStore.class);
        SignupEmailProofStore proofs = mock(SignupEmailProofStore.class);
        VerificationEmailSender sender = mock(VerificationEmailSender.class);
        var service = new SignupEmailVerificationService(codes, proofs, sender,
                Base64.getEncoder().encodeToString(new byte[32]));
        when(codes.verify(eq(email), anyString(), anyString())).thenReturn(0L, -2L);
        doThrow(new EmailVerificationException(Error.SERVICE_UNAVAILABLE)).when(proofs).save(eq(email), any(byte[].class));

        assertThatThrownBy(() -> service.verifyCode(email, "123456"))
                .isInstanceOfSatisfying(EmailVerificationException.class, exception ->
                        assertThat(exception.error()).isEqualTo(Error.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> service.verifyCode(email, "123456"))
                .isInstanceOfSatisfying(EmailVerificationException.class, exception ->
                        assertThat(exception.error()).isEqualTo(Error.CODE_UNAVAILABLE));
        verify(proofs).save(eq(email), any(byte[].class));
        verifyNoMoreInteractions(proofs);
    }
}
