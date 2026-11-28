package com.kjh.auth.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailTest {

    @Test
    void trimsAddressAndNormalizesOnlyDomain() {
        assertThat(new Email(" User.Name+Tag@Example.TEST ").value())
                .isEqualTo("User.Name+Tag@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "user name@example.test", "user\nname@example.test",
            "Ｕser@example.test", "a@@example.test", "user@-example.test"})
    void rejectsInvalidAddresses(String address) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Email(address));
    }
}
