// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ArtifactRegistryMock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class ArtifactExpirerTest {

    @Test
    void maintain() {
        DeploymentTester tester = new DeploymentTester();
        ArtifactExpirer expirer = new ArtifactExpirer(tester.controller(), Duration.ofDays(1));
        ArtifactRegistryMock registry = tester.controllerTester().serviceRegistry().artifactRegistry(CloudName.DEFAULT).orElseThrow();

        Instant instant = tester.clock().instant();
        Artifact image0 = new Artifact("image0", "registry.example.com", "vespa/vespa", "7.1", instant, Version.fromString("7.1"));
        Artifact image1 = new Artifact("image1", "registry.example.com", "vespa/vespa", "7.2.42-amd64", instant, Version.fromString("7.2.42"));
        Artifact image2 = new Artifact("image2", "registry.example.com", "vespa/vespa", "7.2.42.a-amd64", instant, Version.fromString("7.2.42.a"));
        Artifact image3 = new Artifact("image3", "registry.example.com", "vespa/vespa", "7.4-amd64", instant, Version.fromString("7.4"));
        registry.add(image0)
                .add(image1)
                .add(image2)
                .add(image3);

        // Make one image active
        tester.controllerTester().upgradeSystem(image1.version());

        // Nothing is expired initially
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2, image3), registry.list());

        // Nothing is expired as not enough time has passed since image creation
        tester.clock().advance(Duration.ofDays(1));
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2, image3), registry.list());

        // Enough time passes to expire unused image
        tester.clock().advance(Duration.ofDays(13).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertEquals(List.of(image1, image2, image3), registry.list());

        // A new version becomes is published and controllers upgrade. This version, the system version + its unofficial
        // version and future versions are all kept
        Artifact image4 = new Artifact("image4", "registry.example.com", "vespa/vespa", "7.3.0-arm64", tester.clock().instant(), Version.fromString("7.3.0"));
        registry.add(image4);
        tester.controllerTester().upgradeController(image4.version());
        expirer.maintain();
        assertEquals(List.of(image1, image2, image4, image3), registry.list());

        // The system upgrades, only the active and future version are kept
        tester.controllerTester().upgradeSystem(image4.version());
        expirer.maintain();
        assertEquals(List.of(image4, image3), registry.list());
    }

}
