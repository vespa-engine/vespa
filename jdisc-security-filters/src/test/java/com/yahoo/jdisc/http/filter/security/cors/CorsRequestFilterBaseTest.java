// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.ALLOW_ORIGIN_HEADER;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class CorsRequestFilterBaseTest {

    @Test
    public void adds_cors_headers_when_filter_reject_request() {
        String origin = "http://allowed.origin";
        Set<String> allowedOrigins = Collections.singleton(origin);
        int statusCode = 403;
        SimpleCorsRequestFilter filter =
                new SimpleCorsRequestFilter(allowedOrigins, statusCode, "Forbidden");
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader("Origin")).thenReturn(origin);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request, responseHandler);

        Response response = responseHandler.getResponse();
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), equalTo(statusCode));
        List<String> allowOriginHeader = response.headers().get(ALLOW_ORIGIN_HEADER);
        assertThat(allowOriginHeader.size(), equalTo(1));
        assertThat(allowOriginHeader.get(0), equalTo(origin));
    }

    private static class SimpleCorsRequestFilter extends CorsRequestFilterBase {
        private final ErrorResponse errorResponse;

        SimpleCorsRequestFilter(Set<String> allowedUrls, int statusCode, String message) {
            super(allowedUrls);
            this.errorResponse = new ErrorResponse(statusCode, message);
        }

        @Override
        protected Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
            return Optional.ofNullable(this.errorResponse);
        }
    }

}