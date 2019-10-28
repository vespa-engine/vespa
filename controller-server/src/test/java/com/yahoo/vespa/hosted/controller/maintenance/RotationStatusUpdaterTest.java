// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.rotation.RotationState;
import org.junit.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class RotationStatusUpdaterTest {

    @Test
    public void updates_rotation_status() {
        var tester = new InternalDeploymentTester();
        var globalRotationService = tester.controllerTester().serviceRegistry().globalRoutingServiceMock();
        var updater = new RotationStatusUpdater(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()));

        var context = tester.newDeploymentContext(ApplicationId.from("tenant1", "app1", "default"));
        var zone1 = ZoneId.from("prod", "us-west-1");
        var zone2 = ZoneId.from("prod", "us-east-3");
        var zone3 = ZoneId.from("prod", "eu-west-1");

        // Deploy application with global rotation
        var applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region(zone1.region().value())
                .region(zone2.region().value())
                .build();
        context.submit(applicationPackage)
               .deploy();

        // No status gathered yet
        var rotation1 = context.instance().rotations().get(0).rotationId();
        assertEquals(RotationState.unknown, context.instance().rotationStatus().of(rotation1, context.deployment(zone1)));
        assertEquals(RotationState.unknown, context.instance().rotationStatus().of(rotation1, context.deployment(zone2)));

        // First rotation: One zone out, one in
        var rotationName1 = "rotation-fqdn-01";
        globalRotationService.setStatus(rotationName1, zone1, RotationStatus.IN)
                             .setStatus(rotationName1, zone2, RotationStatus.OUT);
        updater.maintain();
        assertEquals(RotationState.in, context.instance().rotationStatus().of(rotation1, context.deployment(zone1)));
        assertEquals(RotationState.out, context.instance().rotationStatus().of(rotation1, context.deployment(zone2)));

        // First rotation: All zones in
        globalRotationService.setStatus(rotationName1, zone2, RotationStatus.IN);
        updater.maintain();
        assertEquals(RotationState.in, context.instance().rotationStatus().of(rotation1, context.deployment(zone1)));
        assertEquals(RotationState.in, context.instance().rotationStatus().of(rotation1, context.deployment(zone2)));

        // Another rotation is assigned
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region(zone1.region().value())
                .region(zone2.region().value())
                .region(zone3.region().value())
                .endpoint("default", "default", "us-east-3", "us-west-1")
                .endpoint("eu", "default", "eu-west-1")
                .build();
        context.submit(applicationPackage)
               .deploy();
        assertEquals(2, context.instance().rotations().size());

        // Second rotation: No status gathered yet
        var rotation2 = context.instance().rotations().get(1).rotationId();
        updater.maintain();
        assertEquals(RotationState.unknown, context.instance().rotationStatus().of(rotation2, context.deployment(zone3)));

        // Status of third zone is retrieved via second rotation
        var rotationName2 = "rotation-fqdn-02";
        globalRotationService.setStatus(rotationName2, zone3, RotationStatus.IN);
        updater.maintain();
        assertEquals(RotationState.in, context.instance().rotationStatus().of(rotation2, context.deployment(zone3)));

        // Each rotation only has status for their configured zones
        assertEquals("Rotation " + rotation1 + " does not know about " + context.deployment(zone3), RotationState.unknown,
                     context.instance().rotationStatus().of(rotation1, context.deployment(zone3)));
        assertEquals("Rotation " + rotation2 + " does not know about " + context.deployment(zone1), RotationState.unknown,
                     context.instance().rotationStatus().of(rotation2, context.deployment(zone1)));
        assertEquals("Rotation " + rotation2 + " does not know about " + context.deployment(zone2), RotationState.unknown,
                     context.instance().rotationStatus().of(rotation2, context.deployment(zone2)));
    }

}
