// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.application.container.handler.Request.Method;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.mock.MockCurator;
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
        tester = filterTester(SystemName.cd);
    }

    @Test
    public void filter() {
        // These are just rudimentary tests of the filter. See AuthorizerTest for more exhaustive tests
        tester.assertRequest(new Request(Method.GET, "/"), 401,
                             "{\"error-code\":\"UNAUTHORIZED\",\"message\":\"GET / denied for " +
                             "remote-addr: Missing credentials\"}");

        tester.assertRequest(new Request(Method.GET, "/").commonName("foo"), 403,
                             "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET / " +
                             "denied for remote-addr: Invalid credentials\"}");

        tester.assertRequest(new Request(Method.GET, "/nodes/v2/node/foo").commonName("bar"),
                              403, "{\"error-code\":\"FORBIDDEN\",\"message\":\"GET /nodes/v2/node/foo " +
                                   "denied for remote-addr: Invalid credentials\"}");

        tester.assertSuccess(new Request(Method.GET, "/nodes/v2/node/foo").commonName("foo"));
    }

    // TODO: Remove once filter applies to all systems
    @Test
    public void filter_does_nothing_in_main_system() {
        FilterTester tester = filterTester(SystemName.main);
        tester.assertSuccess(new Request(Method.GET, "/").commonName("foo"));
        tester.assertSuccess(new Request(Method.GET, "/nodes/v2/node/bar").commonName("foo"));
    }

    private static FilterTester filterTester(SystemName system) {
        Zone zone = new Zone(system, Environment.prod, RegionName.defaultName());
        return new FilterTester(new AuthorizationFilter(zone, new MockNodeRepository(new MockCurator(),
                                                                                     new MockNodeFlavors())));
    }

}
