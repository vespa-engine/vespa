// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.cloudinfo;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * @author freva
 */
class CloudInfoApiHandlerTest extends ControllerContainerTest {
    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/cloudinfo/responses/";

    private ContainerTester tester;

    @Override
    protected SystemName system() {
        return SystemName.cd;
    }

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responses);
        tester.serviceRegistry().zoneRegistry().setZones(
                zone(CloudName.AWS, "prod", "aws-us-east-1a", "use1-az1"),
                zone(CloudName.AWS, "prod", "aws-us-west-2c", "usw2-az4"),
                zone(CloudName.GCP, "prod", "gcp-us-east1-f", "us-east1-f"));
    }

    @Test
    void test_api() {
        tester.assertResponse(authenticatedRequest("http://localhost:8080/cloudinfo/v1/"), new File("root.json"));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/cloudinfo/v1/zones"), new File("zones.json"));
    }

    private static ZoneApi zone(CloudName cloudName, String environment, String region, String availabilityZone) {
        return ZoneApiMock.newBuilder()
                .with(cloudName)
                .with(ZoneId.from(environment, region))
                .withCloudNativeAvailabilityZone(availabilityZone)
                .build();
    }
}
