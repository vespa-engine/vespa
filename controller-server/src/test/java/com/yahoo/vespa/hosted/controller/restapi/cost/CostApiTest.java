// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.config.provision.ZoneId;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
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
    private static final ZoneId zone1 = ZoneId.from("prod", "us-east-3", cloud1.value());
    private static final ZoneId zone2 = ZoneId.from("prod", "us-west-1", cloud1.value());
    private static final ZoneId zone3 = ZoneId.from("prod", "eu-west-1", cloud2.value());

    private ContainerControllerTester tester;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, responses);
        zoneRegistryMock().setSystemName(SystemName.cd)
                .setZones(zone1, zone2, zone3);
    }

    @Test
    public void test_api() {
        assertResponse(new Request("http://localhost:8080/cost/v1/csv"),
                "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n", 200);
    }

    private ZoneRegistryMock zoneRegistryMock() {
        return (ZoneRegistryMock) tester.containerTester().container().components()
                .getComponent(ZoneRegistryMock.class.getName());
    }

    private void assertResponse(Request request, String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }
}
