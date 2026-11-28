package com.kjh.auth.config;

import com.kjh.auth.adapter.out.email.VerificationEmailSenderAdapter;
import com.kjh.auth.adapter.out.redis.EmailVerificationCodeAdapter;
import com.kjh.auth.adapter.out.redis.SignupEmailProofAdapter;
import com.kjh.auth.application.service.SignupEmailVerificationService;
import com.kjh.auth.application.port.out.VerificationEmailSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class SignupEmailVerificationConfig {

    @Bean
    VerificationEmailSender verificationEmailSender(@Value("${auth.signup.email.from}") String from,
            @Value("${auth.signup.email.brevo-api-key}") String apiKey) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(5));
        RestClient client = RestClient.builder().baseUrl("https://api.brevo.com")
                .requestFactory(requests).build();
        return new VerificationEmailSenderAdapter(client, from, apiKey);
    }

    @Bean
    SignupEmailVerificationService signupEmailVerificationService(StringRedisTemplate redis,
            VerificationEmailSender sender,
            @Value("${auth.signup.email.hmac-key}") String hmacKey,
            @Value("${auth.signup.email.global-send-limit-per-minute:100}") int globalSendLimit) {
        return new SignupEmailVerificationService(new EmailVerificationCodeAdapter(redis, globalSendLimit),
                new SignupEmailProofAdapter(redis), sender, hmacKey);
    }
}
