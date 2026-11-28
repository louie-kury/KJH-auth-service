package com.kjh.auth.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.regex.Pattern;

final class ClientIp {

    private static final Pattern IPV4 = Pattern.compile(
            "(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final String trustedGateway;

    ClientIp(String trustedGateway) {
        this.trustedGateway = literal(trustedGateway);
        if (!trustedGateway.isBlank() && this.trustedGateway == null) {
            throw new IllegalArgumentException("Configure the gateway as a literal IP address");
        }
    }

    String from(HttpServletRequest request) {
        String peer = literal(request.getRemoteAddr());
        if (trustedGateway == null || !trustedGateway.equals(peer)) {
            return peer;
        }
        Enumeration<String> values = request.getHeaders("X-Client-IP");
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? null : literal(value);
    }

    private static String literal(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.indexOf(':') >= 0) {
            if (!IPV6.matcher(raw).matches()) {
                return null;
            }
            try {
                return InetAddress.getByName(raw).getHostAddress();
            } catch (UnknownHostException exception) {
                return null;
            }
        }
        if (!IPV4.matcher(raw).matches()) {
            return null;
        }
        for (String part : raw.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return null;
            }
        }
        return raw;
    }
}
