package com.kjh.auth.application.port.out;

import com.kjh.auth.domain.model.Email;

public interface SignupEmailProofStore {

    void save(Email email, byte[] proofHash);
}
