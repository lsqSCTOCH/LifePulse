package com.hmdp.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IpLocationServiceTest {
    @Test
    void publicClientIpOverridesSpoofedForwardedHeaderAndCachesLookup() {
        RestTemplate http = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://ipwho.is/8.8.4.4")).andRespond(withSuccess(
                "{\"success\":true,\"city\":\"Shenzhen\"}", MediaType.APPLICATION_JSON));
        IpLocationService service = new IpLocationService(http);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("8.8.4.4");
        request.addHeader("X-Real-IP", "1.1.1.1");
        assertThat(service.locate(request).get("city")).isEqualTo("深圳");
        assertThat(service.locate(request).get("city")).isEqualTo("深圳");
        server.verify();
    }

    @Test
    void trustsLocalNginxHeaderForPublicClient() {
        RestTemplate http = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://ipwho.is/1.1.1.1")).andRespond(withSuccess(
                "{\"success\":true,\"city\":\"Chengdu\"}", MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "1.1.1.1");
        assertThat(new IpLocationService(http).locate(request).get("city")).isEqualTo("成都"); server.verify();
    }

    @Test
    void localhostUsesEgressLookupRatherThanFixedHangzhou() {
        RestTemplate http = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://ipwho.is/")).andRespond(withSuccess(
                "{\"success\":true,\"city\":\"Shanghai\"}", MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        Map<String, Object> result = new IpLocationService(http).locate(request);
        assertThat(result.get("city")).isEqualTo("上海");assertThat(result.get("source")).isEqualTo("local-network-ip");
        server.verify();
    }

    @Test
    void unavailableProviderDoesNotPretendToLocateCity() {
        RestTemplate http = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://ipwho.is/8.8.4.4")).andRespond(withSuccess(
                "{\"success\":false}", MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("8.8.4.4");
        Map<String, Object> result = new IpLocationService(http).locate(request);
        assertThat(result.get("city")).isEqualTo("");assertThat(result.get("located")).isEqualTo(false);server.verify();
    }

    @Test
    void privateClientCannotBeLocatedAndDoesNotCallProvider() {
        RestTemplate http = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.createServer(http);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("192.168.1.10");
        assertThat(new IpLocationService(http).locate(request).get("located")).isEqualTo(false);server.verify();
    }
}
