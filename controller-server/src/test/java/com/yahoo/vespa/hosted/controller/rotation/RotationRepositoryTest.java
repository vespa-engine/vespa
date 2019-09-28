// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.List;
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
                .rotations("foo-1", "\n       foo-1.com      \n")
                .rotations("foo-2", "foo-2.com")
        );

    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .globalServiceId("foo")
            .region("us-east-3")
            .region("us-west-1")
            .build();

    private DeploymentTester tester;
    private RotationRepository repository;
    private Application application;
    private Instance instance;
    
    @Before
    public void before() {
        tester = new DeploymentTester(new ControllerTester(rotationsConfig));
        repository = tester.controller().applications().rotationRepository();
        application = tester.createApplication("app1", "tenant1", 11L, 1L);
        instance = tester.defaultInstance(application.id());
    }

    @Test
    public void assigns_and_reuses_rotation() {
        // Deploying assigns a rotation
        tester.deployCompletely(application, applicationPackage);
        Rotation expected = new Rotation(new RotationId("foo-1"), "foo-1.com");

        instance = tester.applications().requireInstance(instance.id());
        assertEquals(List.of(expected.id()), rotationIds(instance.rotations()));
        assertEquals(URI.create("https://app1--tenant1.global.vespa.oath.cloud:4443/"),
                     instance.endpointsIn(SystemName.main).main().get().url());
        try (RotationLock lock = repository.lock()) {
            Rotation rotation = repository.getOrAssignRotation(application.deploymentSpec(), tester.applications().requireInstance(instance.id()), lock);
            assertEquals(expected, rotation);
        }

        // Deploying once more assigns same rotation
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .region("us-west-1")
                .searchDefinition("search foo { }") // Update application package so there is something to deploy
                .build();
        tester.deployCompletely(application, applicationPackage, 43);
        assertEquals(List.of(expected.id()), rotationIds(tester.applications().requireInstance(instance.id()).rotations()));
    }
    
    @Test
    public void strips_whitespace_in_rotation_fqdn() {
        DeploymentTester tester = new DeploymentTester(new ControllerTester(rotationsConfigWhitespaces));
        RotationRepository repository = tester.controller().applications().rotationRepository();
        Application application2 = tester.createApplication("app2", "tenant2", 22L, 2L);

        tester.deployCompletely(application2, applicationPackage);
        Instance instance2 = tester.defaultInstance(application2.id());

        try (RotationLock lock = repository.lock()) {
            Rotation rotation = repository.getOrAssignRotation(application2.deploymentSpec(), instance2, lock);
            Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
            assertEquals(assignedRotation, rotation);
        }
    }

    @Test
    public void out_of_rotations() {
        // Assigns 1 rotation
        tester.deployCompletely(application, applicationPackage);

        // Assigns 1 more
        Application application2 = tester.createApplication("app2", "tenant2", 22L, 2L);
        tester.deployCompletely(application2, applicationPackage);

        // We're now out of rotations
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("no rotations available");
        Application application3 = tester.createApplication("app3", "tenant3", 33L, 3L);
        tester.deployCompletely(application3, applicationPackage);
    }

    @Test
    public void too_few_zones() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .build();
        Application application2 = tester.createApplication("app2", "tenant2", 22L, 2L);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");
        tester.deployCompletely(application2, applicationPackage);
    }

    @Test
    public void no_rotation_assigned_for_application_without_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .region("us-west-1")
                .build();
        tester.deployCompletely(application, applicationPackage);
        assertTrue(tester.defaultInstance(application.id()).rotations().isEmpty());
    }

    @Test
    public void prefixes_system_when_not_main() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .region("us-west-1")
                .build();
        Application application2 = tester.createApplication("app2", "tenant2", 22L, 2L);
        tester.deployCompletely(application2, applicationPackage);
        assertEquals(List.of(new RotationId("foo-1")), rotationIds(tester.defaultInstance(application2.id()).rotations()));
        assertEquals("https://cd--app2--tenant2.global.vespa.oath.cloud:4443/", tester.defaultInstance(application2.id())
                .endpointsIn(SystemName.cd).main().get().url().toString());
    }

    private static List<RotationId> rotationIds(List<AssignedRotation> assignedRotations) {
        return assignedRotations.stream().map(AssignedRotation::rotationId).collect(Collectors.toUnmodifiableList());
    }

}
