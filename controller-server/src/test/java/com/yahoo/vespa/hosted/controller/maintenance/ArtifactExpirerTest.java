// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.api.integration.container.ContainerImage.Architecture;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ArtifactRegistryMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ArtifactExpirerTest {

    @Test
    public void maintain() {
        DeploymentTester tester = new DeploymentTester();
        ArtifactExpirer expirer = new ArtifactExpirer(tester.controller(), Duration.ofDays(1));
        ArtifactRegistryMock registry = tester.controllerTester().serviceRegistry().containerRegistry();

        Instant instant = tester.clock().instant();
        Artifact image0 = new Artifact("image0", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.1"), Optional.empty());
        Artifact image1 = new Artifact("image1", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.2"), Optional.of(Architecture.amd64));
        Artifact image2 = new Artifact("image2", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.4"), Optional.of(Architecture.amd64));
        registry.add(image0)
                .add(image1)
                .add(image2);

        // Make one image active
        tester.controllerTester().upgradeSystem(image1.version());

        // Nothing is expired initially
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2), registry.list());

        // Nothing happens as not enough time has passed since image creation
        tester.clock().advance(Duration.ofDays(1));
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2), registry.list());

        // Enough time passes to expire unused image
        tester.clock().advance(Duration.ofDays(13).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertEquals(List.of(image1, image2), registry.list());

        // A new version becomes active. The active and future version are kept
        Artifact image3 = new Artifact("image3", "registry.example.com", "vespa/vespa", tester.clock().instant(), Version.fromString("7.3"), Optional.of(Architecture.amd64));
        registry.add(image3);
        tester.controllerTester().upgradeSystem(image3.version());
        expirer.maintain();
        assertEquals(List.of(image3, image2), registry.list());
    }

}
