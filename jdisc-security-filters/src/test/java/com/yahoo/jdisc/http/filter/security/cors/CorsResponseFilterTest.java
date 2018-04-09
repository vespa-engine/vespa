// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig.Builder;
import com.yahoo.jdisc.http.servlet.ServletOrJdiscHttpResponse;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ACCESS_CONTROL_HEADERS;
import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ALLOW_ORIGIN_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author gjoranv
 * @author bjorncs
 */
public class CorsResponseFilterTest {

    @Test
    public void any_request_yields_access_control_headers_in_response() {
        Map<String, String> headers = doFilterRequest(newResponseFilter(), "http://any.origin");
        ACCESS_CONTROL_HEADERS.keySet().forEach(
                header -> assertFalse("Empty header: " + header, headers.get(header).isEmpty()));
    }

    @Test
    public void allowed_request_origin_yields_allow_origin_header_in_response() {
        final String ALLOWED_ORIGIN = "http://allowed.origin";
        Map<String, String> headers = doFilterRequest(newResponseFilter(ALLOWED_ORIGIN), ALLOWED_ORIGIN);
        assertEquals(ALLOWED_ORIGIN, headers.get(ALLOW_ORIGIN_HEADER));
    }

    @Test
    public void disallowed_request_origin_does_not_yield_allow_origin_header_in_response() {
        Map<String, String> headers = doFilterRequest(newResponseFilter("http://allowed.origin"), "http://disallowed.origin");
        assertNull(headers.get(ALLOW_ORIGIN_HEADER));
    }

    @Test
    public void any_request_origin_yields_allow_origin_header_in_response_when_wildcard_is_allowed() {
        Map<String, String> headers = doFilterRequest(newResponseFilter("*"), "http://any.origin");
        assertEquals("*", headers.get(ALLOW_ORIGIN_HEADER));
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
        Map<String, String> headers = new HashMap<>();

        TestResponse() {
            super(mock(ServletOrJdiscHttpResponse.class));
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public void removeHeaders(String s) { throw new UnsupportedOperationException(); }

        @Override
        public void setHeaders(String s, String s1) { throw new UnsupportedOperationException(); }

        @Override
        public void setHeaders(String s, List<String> list) { throw new UnsupportedOperationException(); }

        @Override
        public void addHeader(String s, String s1) { throw new UnsupportedOperationException(); }

        @Override
        public void setCookies(List<Cookie> list) { throw new UnsupportedOperationException(); }

        @Override
        public void setStatus(int i) { throw new UnsupportedOperationException(); }
    }
}
