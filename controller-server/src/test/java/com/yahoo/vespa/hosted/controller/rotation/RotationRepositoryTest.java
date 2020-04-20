// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Oyvind Gronnesby
 * @author mpolden
 */
public class RotationRepositoryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final RotationsConfig rotationsConfig = new RotationsConfig(
        new RotationsConfig.Builder()
            .rotations("foo-1", "foo-1.com")
            .rotations("foo-2", "foo-2.com")
    );

    private final RotationsConfig rotationsConfigWhitespaces = new RotationsConfig(
            new RotationsConfig.Builder()
                .rotations("foo-1", "\n  \t     foo-1.com      \n")
                .rotations("foo-2", "foo-2.com")
        );

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .globalServiceId("foo")
            .region("us-east-3")
            .region("us-west-1")
            .build();

    private final DeploymentTester tester = new DeploymentTester(new ControllerTester(rotationsConfig));
    private final RotationRepository repository = tester.controller().routing().rotations();
    private final DeploymentContext application = tester.newDeploymentContext("tenant1", "app1", "default");

    @Test
    public void assigns_and_reuses_rotation() {
        // Submitting assigns a rotation
        application.submit(applicationPackage);
        Rotation expected = new Rotation(new RotationId("foo-1"), "foo-1.com");

        assertEquals(List.of(expected.id()), rotationIds(application.instance().rotations()));
        assertEquals(URI.create("https://app1--tenant1.global.vespa.oath.cloud:4443/"),
                     tester.controller().routing().endpointsOf(application.instanceId()).primary().get().url());
        try (RotationLock lock = repository.lock()) {
            List<AssignedRotation> rotations = repository.getOrAssignRotations(application.application().deploymentSpec(),
                                                                               application.instance(),
                                                                              lock);
            assertSingleRotation(expected, rotations, repository);
            assertEquals(Set.of(RegionName.from("us-west-1"), RegionName.from("us-east-3")),
                         application.instance().rotations().get(0).regions());
        }

        // Submitting once more assigns same rotation
        application.submit(applicationPackage).deploy();
        assertEquals(List.of(expected.id()), rotationIds(application.instance().rotations()));

        // Adding region updates rotation
        var applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .region("us-west-1")
                .region("us-central-1")
                .build();
        application.submit(applicationPackage).deploy();
        assertEquals(Set.of(RegionName.from("us-west-1"), RegionName.from("us-east-3"),
                     RegionName.from("us-central-1")),
                     application.instance().rotations().get(0).regions());
    }
    
    @Test
    public void strips_whitespace_in_rotation_fqdn() {
        var tester = new DeploymentTester(new ControllerTester(rotationsConfigWhitespaces));
        RotationRepository repository = tester.controller().routing().rotations();
        var application2 = tester.newDeploymentContext("tenant1", "app2", "default");

        application2.submit(applicationPackage);

        try (RotationLock lock = repository.lock()) {
            List<AssignedRotation> rotations = repository.getOrAssignRotations(application2.application().deploymentSpec(), application2.instance(), lock);
            Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
            assertSingleRotation(assignedRotation, rotations, repository);
        }
    }

    @Test
    public void out_of_rotations() {
        // Assigns 1 rotation
        application.submit(applicationPackage);

        // Assigns 1 more
        var application2 = tester.newDeploymentContext("tenant2", "app2", "default");
        application2.submit(applicationPackage);

        // We're now out of rotations
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("out of rotations");
        var application3 = tester.newDeploymentContext("tenant3", "app3", "default");
        application3.submit(applicationPackage);
    }

    @Test
    public void too_few_zones() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .build();
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");
        application.submit(applicationPackage);
    }

    @Test
    public void no_rotation_assigned_for_application_without_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .region("us-west-1")
                .build();
        application.submit(applicationPackage);
        assertTrue(application.instance().rotations().isEmpty());
    }

    @Test
    public void prefixes_system_when_not_main() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("cd-us-central-1")
                .region("cd-us-west-1")
                .build();
        var zones = List.of(ZoneApiMock.fromId("prod.cd-us-central-1"), ZoneApiMock.fromId("prod.cd-us-west-1"));
        tester.controllerTester().zoneRegistry()
              .setZones(zones)
              .setRoutingMethod(zones, RoutingMethod.shared)
              .setSystemName(SystemName.cd);
        var application2 = tester.newDeploymentContext("tenant2", "app2", "default");
        application2.submit(applicationPackage);
        assertEquals(List.of(new RotationId("foo-1")), rotationIds(application2.instance().rotations()));
        assertEquals("https://cd--app2--tenant2.global.vespa.oath.cloud:4443/",
                     tester.controller().routing().endpointsOf(application2.instanceId()).primary().get().url().toString());
    }

    @Test
    public void multiple_instances_with_similar_global_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .globalServiceId("global")
                .build();
        var instance1 = tester.newDeploymentContext("tenant1", "application1", "instance1").submit(applicationPackage);
        var instance2 = tester.newDeploymentContext("tenant1", "application1", "instance2");
        assertEquals(List.of(new RotationId("foo-1")), rotationIds(instance1.instance().rotations()));
        assertEquals(List.of(new RotationId("foo-2")), rotationIds(instance2.instance().rotations()));
        assertEquals(URI.create("https://instance1--application1--tenant1.global.vespa.oath.cloud:4443/"),
                     tester.controller().routing().endpointsOf(instance1.instanceId()).primary().get().url());
        assertEquals(URI.create("https://instance2--application1--tenant1.global.vespa.oath.cloud:4443/"),
                     tester.controller().routing().endpointsOf(instance2.instanceId()).primary().get().url());
    }

    @Test
    public void multiple_instances_with_similar_endpoints() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .endpoint("default", "foo", "us-central-1", "us-west-1")
                .build();
        var instance1 = tester.newDeploymentContext("tenant1", "application1", "instance1").submit(applicationPackage);
        var instance2 = tester.newDeploymentContext("tenant1", "application1", "instance2");

        assertEquals(List.of(new RotationId("foo-1")), rotationIds(instance1.instance().rotations()));
        assertEquals(List.of(new RotationId("foo-2")), rotationIds(instance2.instance().rotations()));

        assertEquals(URI.create("https://instance1--application1--tenant1.global.vespa.oath.cloud:4443/"),
                     tester.controller().routing().endpointsOf(instance1.instanceId()).primary().get().url());
        assertEquals(URI.create("https://instance2--application1--tenant1.global.vespa.oath.cloud:4443/"),
                     tester.controller().routing().endpointsOf(instance2.instanceId()).primary().get().url());
    }

    private void assertSingleRotation(Rotation expected, List<AssignedRotation> assignedRotations, RotationRepository repository) {
        assertEquals(1, assignedRotations.size());
        var rotationId = assignedRotations.get(0).rotationId();
        var rotation = repository.getRotation(rotationId);
        assertTrue(rotationId + " exists", rotation.isPresent());
        assertEquals(expected, rotation.get());
    }

    private static List<RotationId> rotationIds(List<AssignedRotation> assignedRotations) {
        return assignedRotations.stream().map(AssignedRotation::rotationId).collect(Collectors.toUnmodifiableList());
    }

}
