// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.application.container.handler.Request.Method;
import com.yahoo.vespa.hosted.provision.restapi.v2.filter.FilterTester.Request;
import org.junit.Before;
import org.junit.Test;

/**
 * @author mpolden
 */
public class LocalhostFilterTest {

    private FilterTester tester;

    @Before
    public void before() {
        tester = new FilterTester(new LocalhostFilter());
    }

    @Test
    public void filter() {
        // Reject from non-loopback
        tester.assertRequest(new Request(Method.GET, "/").remoteAddr("1.2.3.4"), 401,
                             "{\"error-code\":\"UNAUTHORIZED\",\"message\":\"GET / denied for " +
                             "1.2.3.4: Unauthorized host\"}");

        // Allow requests from loopback addresses
        tester.assertSuccess(new Request(Method.GET, "/").remoteAddr("127.0.0.1"));
        tester.assertSuccess(new Request(Method.GET, "/").remoteAddr("127.127.0.1"));
        tester.assertSuccess(new Request(Method.GET, "/").remoteAddr("0:0:0:0:0:0:0:1"));

        // Allow requests originating from same host
        tester.assertSuccess(new Request(Method.GET, "/").localAddr("1.3.3.7").remoteAddr("1.3.3.7"));
    }

}
