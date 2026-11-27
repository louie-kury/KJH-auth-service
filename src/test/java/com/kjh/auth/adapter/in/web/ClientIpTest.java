package com.kjh.auth.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    private final ClientIp clientIp = new ClientIp("127.0.0.1");

    @Test
    void acceptsSingleIpFromTrustedGateway() {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Client-IP", "192.0.2.10");

        assertThat(clientIp.from(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void ignoresClaimedIpFromDirectPeer() {
        MockHttpServletRequest request = requestFrom("127.0.0.2");
        request.addHeader("X-Client-IP", "192.0.2.10");

        assertThat(clientIp.from(request)).isEqualTo("127.0.0.2");
    }

    @Test
    void acceptsDirectRequestsWithoutGatewayConfigurationOrHeaders() {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        assertThat(new ClientIp("").from(request)).isEqualTo("127.0.0.1");
        request.addHeader("X-Client-IP", "192.0.2.10");
        assertThat(new ClientIp("").from(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsMissingRepeatedAndNonLiteralClientIp() {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        assertThat(clientIp.from(request)).isNull();

        request.addHeader("X-Client-IP", "192.0.2.10");
        request.addHeader("X-Client-IP", "192.0.2.11");
        assertThat(clientIp.from(request)).isNull();

        for (String value : new String[]{"192.0.2.10, 192.0.2.11", "localhost", "192.0.2"}) {
            MockHttpServletRequest invalid = requestFrom("127.0.0.1");
            invalid.addHeader("X-Client-IP", value);
            assertThat(clientIp.from(invalid)).isNull();
        }
    }

    private MockHttpServletRequest requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }
}
