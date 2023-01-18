// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.rotation;

import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Oyvind Gronnesby
 * @author mpolden
 */
public class RotationRepositoryTest {

    private static final RotationsConfig rotationsConfig = new RotationsConfig(
        new RotationsConfig.Builder()
            .rotations("foo-1", "foo-1.com")
            .rotations("foo-2", "foo-2.com")
    );

    private static final RotationsConfig rotationsConfigWhitespaces = new RotationsConfig(
            new RotationsConfig.Builder()
                .rotations("foo-1", "\n  \t     foo-1.com      \n")
                .rotations("foo-2", "foo-2.com")
        );

    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .globalServiceId("foo")
            .region("us-east-3")
            .region("us-west-1")
            .build();

    private final DeploymentTester tester = new DeploymentTester(new ControllerTester(rotationsConfig, SystemName.main));
    private final RotationRepository repository = tester.controller().routing().rotations();
    private final DeploymentContext application = tester.newDeploymentContext("tenant1", "app1", "default");

    @Test
    void assigns_and_reuses_rotation() {
        // Deploying assigns a rotation
        application.submit(applicationPackage).deploy();
        Rotation expected = new Rotation(new RotationId("foo-1"), "foo-1.com");

        assertEquals(List.of(expected.id()), rotationIds(application.instance().rotations()));
        assertEquals(URI.create("https://app1.tenant1.global.vespa.oath.cloud/"),
                tester.controller().routing().readDeclaredEndpointsOf(application.instanceId()).direct().first().get().url());
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
    void strips_whitespace_in_rotation_fqdn() {
        var tester = new DeploymentTester(new ControllerTester(rotationsConfigWhitespaces, SystemName.main));
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
    void out_of_rotations() {
        // Assigns 1 rotation
        application.submit(applicationPackage).deploy();

        // Assigns 1 more
        var application2 = tester.newDeploymentContext("tenant2", "app2", "default");
        application2.submit(applicationPackage).deploy();

        // We're now out of rotations and next deployment fails
        var application3 = tester.newDeploymentContext("tenant3", "app3", "default");
        application3.submit(applicationPackage)
                    .runJobExpectingFailure(DeploymentContext.systemTest, "out of rotations");
    }

    @Test
    void too_few_zones() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .build();
        application.submit(applicationPackage).runJobExpectingFailure(DeploymentContext.systemTest, "less than 2 prod zones are defined");
    }

    @Test
    void no_rotation_assigned_for_application_without_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .region("us-west-1")
                .build();
        application.submit(applicationPackage);
        assertTrue(application.instance().rotations().isEmpty());
    }

    @Test
    void prefixes_system_when_not_main() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("cd-us-east-1")
                .region("cd-us-west-1")
                .build();
        var zones = List.of(
                ZoneApiMock.fromId("test.cd-us-west-1"),
                ZoneApiMock.fromId("staging.cd-us-west-1"),
                ZoneApiMock.fromId("prod.cd-us-east-1"),
                ZoneApiMock.fromId("prod.cd-us-west-1"));
        DeploymentTester tester = new DeploymentTester(new ControllerTester(rotationsConfig, SystemName.cd));
        tester.controllerTester().zoneRegistry()
                .setZones(zones)
                .setRoutingMethod(zones, RoutingMethod.sharedLayer4);
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.notController());
        var application2 = tester.newDeploymentContext("tenant2", "app2", "default");
        application2.submit(applicationPackage).deploy();
        assertEquals(List.of(new RotationId("foo-1")), rotationIds(application2.instance().rotations()));
        assertEquals("https://cd.app2.tenant2.global.vespa.oath.cloud/",
                tester.controller().routing().readDeclaredEndpointsOf(application2.instanceId()).primary().get().url().toString());
    }

    @Test
    void multiple_instances_with_similar_global_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .globalServiceId("global")
                .build();
        var instance1 = tester.newDeploymentContext("tenant1", "application1", "instance1")
                .submit(applicationPackage)
                .deploy();
        var instance2 = tester.newDeploymentContext("tenant1", "application1", "instance2");
        assertEquals(List.of(new RotationId("foo-1")), rotationIds(instance1.instance().rotations()));
        assertEquals(List.of(new RotationId("foo-2")), rotationIds(instance2.instance().rotations()));
        assertEquals(URI.create("https://instance1.application1.tenant1.global.vespa.oath.cloud/"),
                tester.controller().routing().readDeclaredEndpointsOf(instance1.instanceId()).direct().first().get().url());
        assertEquals(URI.create("https://instance2.application1.tenant1.global.vespa.oath.cloud/"),
                tester.controller().routing().readDeclaredEndpointsOf(instance2.instanceId()).direct().first().get().url());
    }

    @Test
    void multiple_instances_with_similar_endpoints() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .endpoint("default", "foo", "us-central-1", "us-west-1")
                .build();
        var instance1 = tester.newDeploymentContext("tenant1", "application1", "instance1")
                .submit(applicationPackage)
                .deploy();
        var instance2 = tester.newDeploymentContext("tenant1", "application1", "instance2");

        assertEquals(List.of(new RotationId("foo-1")), rotationIds(instance1.instance().rotations()));
        assertEquals(List.of(new RotationId("foo-2")), rotationIds(instance2.instance().rotations()));

        assertEquals(URI.create("https://instance1.application1.tenant1.global.vespa.oath.cloud/"),
                tester.controller().routing().readDeclaredEndpointsOf(instance1.instanceId()).direct().first().get().url());
        assertEquals(URI.create("https://instance2.application1.tenant1.global.vespa.oath.cloud/"),
                tester.controller().routing().readDeclaredEndpointsOf(instance2.instanceId()).direct().first().get().url());
    }

    private void assertSingleRotation(Rotation expected, List<AssignedRotation> assignedRotations, RotationRepository repository) {
        assertEquals(1, assignedRotations.size());
        RotationId rotationId = assignedRotations.get(0).rotationId();
        Rotation rotation = repository.requireRotation(rotationId);
        assertEquals(expected, rotation);
    }

    private static List<RotationId> rotationIds(List<AssignedRotation> assignedRotations) {
        return assignedRotations.stream().map(AssignedRotation::rotationId).toList();
    }

}
