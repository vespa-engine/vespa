// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig.Builder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ACCESS_CONTROL_HEADERS;
import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ALLOW_ORIGIN_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author gjoranv
 * @author bjorncs
 */
public class CorsResponseFilterTest {

    @Test
    void any_request_yields_access_control_headers_in_response() {
        Map<String, String> headers = doFilterRequest(newResponseFilter(), "http://any.origin");
        ACCESS_CONTROL_HEADERS.keySet().forEach(
                header -> assertFalse(headers.get(header).isEmpty(), "Empty header: " + header));
    }

    @Test
    void allowed_request_origin_yields_allow_origin_header_in_response() {
        final String ALLOWED_ORIGIN = "http://allowed.origin";
        Map<String, String> headers = doFilterRequest(newResponseFilter(ALLOWED_ORIGIN), ALLOWED_ORIGIN);
        assertEquals(ALLOWED_ORIGIN, headers.get(ALLOW_ORIGIN_HEADER));
    }

    @Test
    void disallowed_request_origin_does_not_yield_allow_origin_header_in_response() {
        Map<String, String> headers = doFilterRequest(newResponseFilter("http://allowed.origin"), "http://disallowed.origin");
        assertNull(headers.get(ALLOW_ORIGIN_HEADER));
    }

    @Test
    void any_request_origin_yields_allow_origin_header_in_response_when_wildcard_is_allowed() {
        Map<String, String> headers = doFilterRequest(newResponseFilter("*"), "http://any.origin");
        assertEquals("http://any.origin", headers.get(ALLOW_ORIGIN_HEADER));
    }

    private static Map<String, String> doFilterRequest(SecurityResponseFilter filter, String originUrl) {
        TestResponse response = new TestResponse();
        filter.filter(response, newRequestView(originUrl));
        return Collections.unmodifiableMap(response.headers);
    }

    private static CorsResponseFilter newResponseFilter(String... allowedOriginUrls) {
        Builder builder = new Builder();
        Arrays.asList(allowedOriginUrls).forEach(builder::allowedUrls);
        return new CorsResponseFilter(new CorsFilterConfig(builder));
    }

    private static RequestView newRequestView(String originUrl) {
        RequestView request = mock(RequestView.class);
        when(request.getFirstHeader("Origin")).thenReturn(Optional.of(originUrl));
        return request;
    }

    private static class TestResponse extends DiscFilterResponse {
        final Map<String, String> headers = new HashMap<>();

        TestResponse() { super(HttpResponse.newInstance(200)); }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }
    }
}
