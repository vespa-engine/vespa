// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v1;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
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
    private static final List<Zone> zones = Arrays.asList(
            new Zone(Environment.prod, RegionName.from("us-north-1")),
            new Zone(Environment.dev, RegionName.from("us-north-2")),
            new Zone(Environment.test, RegionName.from("us-north-3")),
            new Zone(Environment.staging, RegionName.from("us-north-4"))
    );

    @Before
    public void before() {
        ZoneRegistryMock zoneRegistry = (ZoneRegistryMock) container.components()
                                                                    .getComponent(ZoneRegistryMock.class.getName());
        zoneRegistry.setDefaultRegionForEnvironment(Environment.dev, RegionName.from("us-north-2"))
                    .setZones(zones);
    }

    @Test
    public void test_requests_v1() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1"),
                                                new File("root.json"));
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1/environment/prod"),
                                                new File("prod.json"));
        tester.containerTester().assertResponse(new Request("http://localhost:8080/zone/v1/environment/dev/default"),
                                                new File("default-for-region.json"));
    }

}
