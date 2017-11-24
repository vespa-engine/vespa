// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.api.identifiers.RotationId;
import com.yahoo.vespa.hosted.controller.api.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.MemoryControllerDb;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.StringReader;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Oyvind Gronnesby
 */
public class ControllerRotationRepositoryTest {

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
    private final ControllerDb controllerDb = new MemoryControllerDb();
    private final ApplicationId applicationId = ApplicationId.from("msbe", "tumblr-search", "default");

    @Rule public ExpectedException thrown = ExpectedException.none();

    private final DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(
        new StringReader(
                "<deployment>" +
                "  <prod global-service-id='foo'>" +
                "    <region active='true'>us-east</region>" +
                "    <region active='true'>us-west</region>" +
                "  </prod>" +
                "</deployment>"
        )
    );

    private final DeploymentSpec deploymentSpecOneRegion = DeploymentSpec.fromXml(
        new StringReader(
                "<deployment>" +
                "  <prod global-service-id='nalle'>" +
                "    <region active='true'>us-east</region>" +
                "  </prod>" +
                "</deployment>"
        )
    );

    private final DeploymentSpec deploymentSpecNoServiceId = DeploymentSpec.fromXml(
        new StringReader(
            "<deployment>" +
                "  <prod>" +
                "    <region active='true'>us-east</region>" +
                "    <region active='true'>us-west</region>" +
                "  </prod>" +
                "</deployment>"
        )
    );

    private final DeploymentSpec deploymentSpecOnlyOneNonCorpRegion = DeploymentSpec.fromXml(
        new StringReader(
            "<deployment>" +
            "  <prod global-service-id='nalle'>" +
            "    <region active='true'>us-east</region>" +
            "    <region active='true'>corp-us-west</region>" +
            "  </prod>" +
            "</deployment>"
            )
    );

    private final DeploymentSpec deploymentSpecWithAdditionalCorpZone = DeploymentSpec.fromXml(
            new StringReader(
            "<deployment>" +
            "  <prod global-service-id='nalle'>" +
            "    <region active='true'>us-east</region>" +
            "    <region active='true'>corp-us-west</region>" +
            "    <region active='true'>us-west</region>" +
            "  </prod>" +
            "</deployment>"
            )
    );

    private ControllerRotationRepository repository;
    private ControllerRotationRepository repositoryWhitespaces;
    private Metric metric;
    
    @Before
    public void setup_repository() {
        metric = mock(Metric.class);
        repository = new ControllerRotationRepository(rotationsConfig, controllerDb, metric);
        repositoryWhitespaces = new ControllerRotationRepository(rotationsConfigWhitespaces, controllerDb, metric);
        controllerDb.assignRotation(new RotationId("foo-1"), applicationId);
    }

    @Test
    public void application_with_rotation_reused() {
        Set<Rotation> rotations = repository.getOrAssignRotation(applicationId, deploymentSpec);
        Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
        assertContainsOnly(assignedRotation, rotations);
    }
    
    @Test
    public void names_stripped() {        
        Set<Rotation> rotations = repositoryWhitespaces.getOrAssignRotation(applicationId, deploymentSpec);
        Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
        assertContainsOnly(assignedRotation, rotations);
    }

    @Test
    public void application_without_rotation() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");
        Set<Rotation> rotations = repository.getOrAssignRotation(other, deploymentSpec);
        Rotation assignedRotation = new Rotation(new RotationId("foo-2"), "foo-2.com");
        assertContainsOnly(assignedRotation, rotations);
        verify(metric).set(eq(ControllerRotationRepository.REMAINING_ROTATIONS_METRIC_NAME), eq(1), any());
    }

    @Test
    public void application_without_rotation_but_none_left() {
        application_without_rotation(); // run this test to assign last rotation
        ApplicationId third = ApplicationId.from("thirdtenant", "thirdapplication", "default");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("no rotations available");

        repository.getOrAssignRotation(third, deploymentSpec);
        verify(metric).set(eq(ControllerRotationRepository.REMAINING_ROTATIONS_METRIC_NAME), eq(0), any());
    }

    @Test
    public void application_without_rotation_but_does_not_qualify() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");

        repository.getOrAssignRotation(other, deploymentSpecOneRegion);
    }

    @Test
    public void application_with_rotation_but_does_not_qualify() {
        Set<Rotation> rotations = repository.getOrAssignRotation(applicationId, deploymentSpecOneRegion);
        Rotation assignedRotation = new Rotation(new RotationId("foo-1"), "foo-1.com");
        assertContainsOnly(assignedRotation, rotations);
    }

    @Test
    public void application_with_rotation_is_listed() {
        repository.getOrAssignRotation(applicationId, deploymentSpec);
        Set<URI> uris = repository.getRotationUris(applicationId);
        assertEquals(Collections.singleton(URI.create("http://tumblr-search.msbe.global.vespa.yahooapis.com:4080/")), uris);
    }

    @Test
    public void application_without_rotation_is_empty() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");
        Set<URI> uris = repository.getRotationUris(other);
        assertTrue(uris.isEmpty());
    }

    @Test
    public void application_without_serviceid_and_two_regions() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");
        Set<Rotation> rotations = repository.getOrAssignRotation(other, deploymentSpecNoServiceId);
        assertTrue(rotations.isEmpty());
    }

    @Test
    public void application_with_only_one_non_corp_region() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("less than 2 prod zones are defined");

        repository.getOrAssignRotation(other, deploymentSpecOnlyOneNonCorpRegion);
    }

    @Test
    public void application_with_corp_region_and_two_non_corp_region() {
        ApplicationId other = ApplicationId.from("othertenant", "otherapplication", "default");
        Set<Rotation> rotations = repository.getOrAssignRotation(other, deploymentSpecWithAdditionalCorpZone);
        assertContainsOnly(new Rotation(new RotationId("foo-2"), "foo-2.com"), rotations);
    }

    private static <T> void assertContainsOnly(T item, Collection<T> items) {
        assertTrue("Collection contains only " + item.toString(),
                   items.size() == 1 && items.contains(item));
    }

}
