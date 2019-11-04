// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author andreer
 */
public class CostApiTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/cost/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");
    private static final CloudName cloud1 = CloudName.from("yahoo");
    private static final CloudName cloud2 = CloudName.from("cloud2");
    private static final ZoneApi zone1 = ZoneApiMock.newBuilder().withId("prod.us-east-3").with(cloud1).build();
    private static final ZoneApi zone2 = ZoneApiMock.newBuilder().withId("prod.us-west-1").with(cloud1).build();
    private static final ZoneApi zone3 = ZoneApiMock.newBuilder().withId("prod.eu-west-1").with(cloud2).build();

    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responses);
        tester.serviceRegistry().zoneRegistry().setSystemName(SystemName.cd)
              .setZones(zone1, zone2, zone3);
    }

    @Test
    public void test_api() {
        assertResponse(new Request("http://localhost:8080/cost/v1/csv"),
                "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n", 200);
    }

    private void assertResponse(Request request, String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

}
