// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig.Builder;
import org.junit.Test;

import java.util.Arrays;

import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ACCESS_CONTROL_HEADERS;
import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ALLOW_ORIGIN_HEADER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author gjoranv
 * @author bjorncs
 */
public class CorsPreflightRequestFilterTest {

    @Test
    public void any_options_request_yields_access_control_headers_in_response() {
        HeaderFields headers = doFilterRequest(newRequestFilter(), "http://any.origin");
        ACCESS_CONTROL_HEADERS.keySet().forEach(
                header -> assertFalse("Empty header: " + header, headers.getFirst(header).isEmpty()));
    }

    @Test
    public void allowed_request_origin_yields_allow_origin_header_in_response() {
        final String ALLOWED_ORIGIN = "http://allowed.origin";
        HeaderFields headers = doFilterRequest(newRequestFilter(ALLOWED_ORIGIN), ALLOWED_ORIGIN);
        assertEquals(ALLOWED_ORIGIN, headers.getFirst(ALLOW_ORIGIN_HEADER));
    }

    @Test
    public void disallowed_request_origin_does_not_yield_allow_origin_header_in_response() {
        HeaderFields headers = doFilterRequest(newRequestFilter("http://allowed.origin"), "http://disallowed.origin");
        assertNull(headers.getFirst(ALLOW_ORIGIN_HEADER));
    }

    private static HeaderFields doFilterRequest(SecurityRequestFilter filter, String originUrl) {
        AccessControlResponseHandler responseHandler = new AccessControlResponseHandler();
        filter.filter(newOptionsRequest(originUrl), responseHandler);
        return responseHandler.response.headers();
    }

    private static DiscFilterRequest newOptionsRequest(String origin) {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader("Origin")).thenReturn(origin);
        when(request.getMethod()).thenReturn(OPTIONS.name());
        return request;
    }

    private static CorsPreflightRequestFilter newRequestFilter(String... allowedOriginUrls) {
        Builder builder = new Builder();
        Arrays.asList(allowedOriginUrls).forEach(builder::allowedUrls);
        return new CorsPreflightRequestFilter(new CorsFilterConfig(builder));
    }

    private static class AccessControlResponseHandler implements ResponseHandler {
        Response response;

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            return mock(ContentChannel.class);
        }
    }

}
