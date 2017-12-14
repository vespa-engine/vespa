// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v1;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author mpolden
 */
public class ZoneApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/zone/v1/responses/";
    private static final List<ZoneId> zones = Arrays.asList(
            ZoneId.from(Environment.prod, RegionName.from("us-north-1")),
            ZoneId.from(Environment.dev, RegionName.from("us-north-2")),
            ZoneId.from(Environment.test, RegionName.from("us-north-3")),
            ZoneId.from(Environment.staging, RegionName.from("us-north-4"))
                                                           );

    private ContainerControllerTester tester;

    @Before
    public void before() {
        ZoneRegistryMock zoneRegistry = (ZoneRegistryMock) container.components()
                                                                    .getComponent(ZoneRegistryMock.class.getName());
        zoneRegistry.setDefaultRegionForEnvironment(Environment.dev, RegionName.from("us-north-2"))
                    .setZones(zones);
        this.tester = new ContainerControllerTester(container, responseFiles);
    }

    @Test
    public void test_requests() throws Exception {
        // GET /zone/v1
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1"),
                                                new File("root.json"));

        // GET /zone/v1/environment/prod
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1/environment/prod"),
                                                new File("prod.json"));

        // GET /zone/v1/environment/dev/default
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1/environment/dev/default"),
                                                new File("default-for-region.json"));
    }

    @Test
    public void test_invalid_requests() throws Exception {
        // GET /zone/v1/environment/prod/default: No default region
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1/environment/prod/default"),
                                                new File("no-default-region.json"),
                                                400);
    }

}
