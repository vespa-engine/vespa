// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.application.container.handler.Request.Method;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.restapi.v2.Authorizer;
import com.yahoo.vespa.hosted.provision.restapi.v2.filter.FilterTester.Request;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import org.junit.Before;
import org.junit.Test;

/**
 * @author mpolden
 */
public class AuthorizationFilterTest {

    private FilterTester tester;

    @Before
    public void before() {
        tester = new FilterTester(new AuthorizationFilter(new Authorizer(SystemName.main,
                                                                         new MockNodeRepository(new MockCurator(),
                                                                                                new MockNodeFlavors())),
                                                          FilterUtils::write));
    }

    @Test
    public void filter() {
        // These are just rudimentary tests of the filter. See AuthorizerTest for more exhaustive tests
        tester.assertRequest(new Request(Method.GET, "/"), 401,
                             "{\"error-code\":\"UNAUTHORIZED\",\"message\":\"GET / denied for " +
                             "unit-test: Missing credentials\"}");

        tester.assertRequest(new Request(Method.GET, "/").commonName("foo"), 403,
                             "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET / " +
                             "denied for unit-test: Invalid credentials\"}");

        tester.assertRequest(new Request(Method.GET, "/nodes/v2/node/foo").commonName("bar"),
                              403, "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET /nodes/v2/node/foo " +
                                   "denied for unit-test: Invalid credentials\"}");

        tester.assertSuccess(new Request(Method.GET, "/nodes/v2/node/foo").commonName("foo"));
    }

}
