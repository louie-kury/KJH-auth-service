package com.kjh.auth.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final String ATOM = "[A-Za-z0-9!#$%&'*+/=?^`{|}~_-]+";
    private static final Pattern LOCAL = Pattern.compile(ATOM + "(?:\\." + ATOM + ")*");
    private static final String LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final Pattern DOMAIN = Pattern.compile(LABEL + "(?:\\." + LABEL + ")+");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String trimmed = value.strip();
        int separator = trimmed.indexOf('@');
        if (trimmed.length() > 254 || separator < 1 || separator > 64) {
            throw new IllegalArgumentException("Invalid email format");
        }
        String local = trimmed.substring(0, separator);
        String domain = trimmed.substring(separator + 1);
        if (!LOCAL.matcher(local).matches() || !DOMAIN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        value = local + "@" + domain.toLowerCase(Locale.ROOT);
    }
}
