package com.kjh.auth.adapter.in.web;

import com.kjh.auth.adapter.in.web.dto.SuccessResponse;
import com.kjh.auth.adapter.in.web.dto.FieldError;

import com.kjh.auth.application.port.in.SignupEmailVerificationUseCase;
import com.kjh.auth.domain.model.Email;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;


import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/auth/signup/email")
public final class SignupEmailVerificationController {

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final SignupEmailVerificationUseCase verification;
    private final ClientIp clientIp;
    private final ObjectReader bodyReader;

    public SignupEmailVerificationController(SignupEmailVerificationUseCase verification, ObjectMapper json,
            @Value("${auth.signup.email.trusted-gateway:}") String trustedGateway) {
        this.verification = verification;
        this.clientIp = new ClientIp(trustedGateway);
        this.bodyReader = json.readerFor(new TypeReference<Map<String, Object>>() {})
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(path = "/code")
    public ResponseEntity<?> send(HttpServletRequest request) {
        String ip = clientIp.from(request);
        if (ip == null) {
            throw new UntrustedSourceException();
        }
        Fields fields = validate(readBody(request), false);
        verification.sendCode(fields.email(), ip);
        return ResponseEntity.accepted().body(new SuccessResponse(202, null));
    }

    @PostMapping(path = "/verify")
    public ResponseEntity<?> verify(HttpServletRequest request) {
        String ip = clientIp.from(request);
        if (ip == null) {
            throw new UntrustedSourceException();
        }
        verification.countVerificationRequest(ip);
        Fields fields = validate(readBody(request), true);
        String proof = verification.verifyCode(fields.email(), fields.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessResponse(200, Map.of("emailVerificationProof", proof)));
    }

    private Map<String, Object> readBody(HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            throw new UnsupportedBodyException();
        }
        try {
            Map<String, Object> body = bodyReader.readValue(request.getInputStream());
            if (body != null) {
                return body;
            }
        } catch (IOException | JacksonException exception) {
            throw new ValidationException(null);
        }
        throw new ValidationException(null);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static Fields validate(Map<String, Object> body, boolean requiresCode) {
        List<FieldError> errors = new ArrayList<>();
        Email email = null;
        Object rawEmail = body.get("email");
        if (!(rawEmail instanceof String text)) {
            errors.add(new FieldError("email", EmailVerificationErrorCode.EMAIL_TYPE_MESSAGE));
        } else {
            try {
                email = new Email(text);
            } catch (IllegalArgumentException exception) {
                errors.add(new FieldError("email", EmailVerificationErrorCode.EMAIL_FORMAT_MESSAGE));
            }
        }
        String code = null;
        if (requiresCode) {
            Object rawCode = body.get("code");
            if (rawCode instanceof String text && CODE.matcher(text).matches()) {
                code = text;
            } else {
                errors.add(new FieldError("code", EmailVerificationErrorCode.CODE_FORMAT_MESSAGE));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(List.copyOf(errors));
        }
        return new Fields(email, code);
    }

    private record Fields(Email email, String code) {
    }
}
