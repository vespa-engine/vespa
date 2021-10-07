// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.application.container.handler.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoadBalancersV1ApiTest {

    private RestApiTester tester;

    @Before
    public void createTester() {
        tester = new RestApiTester();
    }

    @After
    public void closeTester() {
        tester.close();
    }

    @Test
    public void test_load_balancers() throws Exception {
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1"), "load-balancers.json");
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1/"), "load-balancers.json");
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant4.application4.instance4"), "load-balancers-single.json");
        tester.assertResponse(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant.nonexistent.default"), "{\"loadBalancers\":[]}");
    }

}
