// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.ApplicationRotation;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Oyvind Gronnesby
 * @author mpolden
 */
public class RotationTest {

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
    
    @Before
    public void before() {
        tester = new DeploymentTester(new ControllerTester(rotationsConfig));
        repository = tester.controller().applications().rotationRepository();
        application = tester.createApplication("app1", "tenant1", 11L,1L);
    }

    @Test
    public void assigns_and_reuses_rotation() {
        // Deploying assigns a rotation
        tester.deployCompletely(application, applicationPackage);
        Rotation expected = new Rotation(new RotationId("foo-1"), "foo-1.com");

        application = tester.applications().require(application.id());
        assertEquals(expected.id(), application.rotation().get().id());
        assertEquals(URI.create("http://app1.tenant1.global.vespa.yahooapis.com:4080/"),
                     application.rotation().get().url());
        try (RotationLock lock = repository.lock()) {
            Rotation rotation = repository.getOrAssignRotation(tester.applications().require(application.id()), lock);
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
        assertEquals(expected.id(), tester.applications().require(application.id()).rotation().get().id());
    }
    
    @Test
    public void strips_whitespace_in_rotation_fqdn() {
        DeploymentTester tester = new DeploymentTester(new ControllerTester(rotationsConfigWhitespaces));
        RotationRepository repository = tester.controller().applications().rotationRepository();
        Application application = tester.createApplication("app2", "tenant2", 22L,
                                                           2L);
        tester.deployCompletely(application, applicationPackage);
        application = tester.applications().require(application.id());

        try (RotationLock lock = repository.lock()) {
            Rotation rotation = repository.getOrAssignRotation(application, lock);
            Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
            assertEquals(assignedRotation, rotation);
        }
    }


    @Test
    public void out_of_rotations() {
        // Assigns 1 rotation
        tester.deployCompletely(application, applicationPackage);

        // Assigns 1 more
        Application application2 = tester.createApplication("app2", "tenant2", 22L,
                                                            2L);
        tester.deployCompletely(application2, applicationPackage);

        // We're now out of rotations
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("no rotations available");
        Application application3 = tester.createApplication("app3", "tenant3", 33L,
                                                            3L);
        tester.deployCompletely(application3, applicationPackage);
    }

    @Test
    public void too_few_zones() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .build();
        Application application = tester.createApplication("app2", "tenant2", 22L,
                                                            2L);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");
        tester.deployCompletely(application, applicationPackage);
    }

    @Test
    public void no_rotation_assigned_for_application_without_service_id() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .region("us-west-1")
                .build();
        tester.deployCompletely(application, applicationPackage);
        Application app = tester.applications().require(application.id());
        Optional<ApplicationRotation> rotation = app.rotation();
        assertFalse(rotation.isPresent());
    }

    @Test
    public void application_with_only_one_non_corp_region() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .region("corp-us-east-1")
                .build();
        Application application = tester.createApplication("app2", "tenant2", 22L,
                                                           2L);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");
        tester.deployCompletely(application, applicationPackage);
    }

    @Test
    public void application_with_corp_region_and_two_non_corp_region() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                .region("us-east-3")
                .region("corp-us-east-1")
                .region("us-west-1")
                .build();
        Application application = tester.createApplication("app2", "tenant2", 22L,
                                                           2L);
        tester.deployCompletely(application, applicationPackage);
        assertEquals(new RotationId("foo-1"), tester.applications().require(application.id())
                                                    .rotation().get().id());
    }

}
