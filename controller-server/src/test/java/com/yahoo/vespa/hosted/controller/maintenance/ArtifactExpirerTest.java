// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ArtifactRegistryMock;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class ArtifactExpirerTest {

    private static final Path configModelPath = Paths.get("src/test/resources/config-models/");

    @Test
    void maintain() {
        DeploymentTester tester = new DeploymentTester();
        // Note: No models in config-models-*.xml
        ArtifactExpirer expirer = new ArtifactExpirer(tester.controller(), Duration.ofDays(1), configModelPath.resolve("empty"));
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

        // A new version is published and controllers upgrade. This version, the system version + its unofficial
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

    @Test
    void maintainWithConfigModelsInUse() {
        DeploymentTester tester = new DeploymentTester(new ControllerTester(SystemName.cd));
        ArtifactExpirer expirer = new ArtifactExpirer(tester.controller(), Duration.ofDays(1), configModelPath.resolve("cd"));
        ArtifactRegistryMock registry = tester.controllerTester().serviceRegistry().artifactRegistry(CloudName.DEFAULT).orElseThrow();

        Instant instant = tester.clock().instant();
        // image0 (with version 8.210.1) is not present in config-models-*.xml
        Artifact image0 = new Artifact("image0", "registry.example.com", "vespa/vespa", "8.210.1", instant, Version.fromString("8.210.1"));
        Artifact image1 = new Artifact("image1", "registry.example.com", "vespa/vespa", "8.220.15", instant, Version.fromString("8.220.15"));
        Artifact image2 = new Artifact("image2", "registry.example.com", "vespa/vespa", "8.223.1", instant, Version.fromString("8.223.1"));

        registry.add(image0)
                .add(image1)
                .add(image2);

        // Make one image active
        tester.controllerTester().upgradeSystem(image1.version());

        // Nothing is expired initially, image2 is not active, but version is one of known config model versions
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2), registry.list());

        // Nothing is expired as not enough time has passed since image creation
        tester.clock().advance(Duration.ofDays(1));
        expirer.maintain();
        assertEquals(List.of(image0, image1, image2), registry.list());

        // Enough time passes to expire unused image
        tester.clock().advance(Duration.ofDays(13).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertEquals(List.of(image1, image2), registry.list());

        // A new version is published and controllers upgrade. This version, the system version + its unofficial
        // version and future versions are all kept
        Artifact image4 = new Artifact("image4", "registry.example.com", "vespa/vespa", "8.223.2-arm64", tester.clock().instant(), Version.fromString("8.223.2"));
        registry.add(image4);
        tester.controllerTester().upgradeController(image4.version());
        expirer.maintain();
        assertEquals(List.of(image1, image2, image4), registry.list());
    }

}
