// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.util.FilterTestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author mpolden
 * @author bjorncs
 */
public class LocalhostFilterTest {

    @Test
    void filter() {
        // Reject from non-loopback
        assertUnauthorized(createRequest("1.2.3.4", null));

        // Allow requests from loopback addresses
        assertSuccess(createRequest("127.0.0.1", null));
        assertSuccess(createRequest("127.127.0.1", null));
        assertSuccess(createRequest("0:0:0:0:0:0:0:1", null));

        // Allow requests originating from self
        assertSuccess(createRequest("1.3.3.7", "1.3.3.7"));
    }

    private static DiscFilterRequest createRequest(String remoteAddr, String localAddr) {
        return FilterTestUtils.newRequestBuilder()
                .withUri("http://%s:8080/".formatted(localAddr))
                .withRemoteAddress(remoteAddr, 12345)
                .build();
    }

    private static void assertUnauthorized(DiscFilterRequest request) {
        LocalhostFilter filter = new LocalhostFilter();
        RequestHandlerTestDriver.MockResponseHandler handler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(request, handler);
        assertEquals(Response.Status.UNAUTHORIZED, handler.getStatus());
    }


    private static void assertSuccess(DiscFilterRequest request) {
        LocalhostFilter filter = new LocalhostFilter();
        RequestHandlerTestDriver.MockResponseHandler handler = new RequestHandlerTestDriver.MockResponseHandler();
        filter.filter(request, handler);
        assertNull(handler.getResponse());
    }

}
