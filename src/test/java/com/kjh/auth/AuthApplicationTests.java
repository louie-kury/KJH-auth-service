package com.kjh.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.kjh.auth.application.port.in.SignupEmailVerificationUseCase;
import com.kjh.auth.application.port.out.VerificationEmailSender;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
class AuthApplicationTests {

    @MockitoBean
    private SignupEmailVerificationUseCase verification;

    @MockitoBean
    private VerificationEmailSender sender;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void contextLoads() {
        assertThat(mappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream()))
                .contains("/api/v1/auth/signup/email/code", "/api/v1/auth/signup/email/verify");
    }
}
