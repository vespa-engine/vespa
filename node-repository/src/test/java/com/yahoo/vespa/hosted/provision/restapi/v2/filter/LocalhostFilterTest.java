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
        tester.assertRequest(new Request(Method.GET, "/").remoteAddr("1.2.3.4"), 401,
                             "{\"error-code\":\"UNAUTHORIZED\",\"message\":\"GET / denied for " +
                             "1.2.3.4: Unauthorized host\"}");

        tester.assertSuccess(new Request(Method.GET, "/").remoteAddr("127.0.0.1"));
        tester.assertSuccess(new Request(Method.GET, "/").remoteAddr("0:0:0:0:0:0:0:1"));
    }

}
