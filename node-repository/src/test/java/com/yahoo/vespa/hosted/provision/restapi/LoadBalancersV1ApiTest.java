// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author mpolden
 */
public class LoadBalancersV1ApiTest {

    private RestApiTester tester;

    @Before
    public void createTester() {
        tester = new RestApiTester(SystemName.main, CloudAccount.empty);
    }

    @After
    public void closeTester() {
        tester.close();
    }

    @Test
    public void load_balancers() throws Exception {
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1"), "load-balancers.json");
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1/"), "load-balancers.json");
        tester.assertFile(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant4.application4.instance4"), "load-balancers-single.json");
        tester.assertResponse(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant.nonexistent.default"), "{\"loadBalancers\":[]}");
    }

    @Test
    public void set_state() throws Exception {
        tester.assertResponse(new Request("http://localhost:8080/loadbalancers/v1/state/removable/tenant42:application42:instance42:id42", "", Request.Method.PUT),
                              404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"load balancer tenant42:application42:instance42:id42 does not exist\"}");
        tester.assertResponse(new Request("http://localhost:8080/loadbalancers/v1/state/removable/tenant4:application4:instance4:id4", "", Request.Method.PUT),
                              "{\"message\":\"Moved load balancer tenant4:application4:instance4:id4 to removable\"}");
    }

}
