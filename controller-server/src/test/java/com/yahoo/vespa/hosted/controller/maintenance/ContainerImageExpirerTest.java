// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.container.ContainerImage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ContainerRegistryMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ContainerImageExpirerTest {

    @Test
    public void maintain() {
        DeploymentTester tester = new DeploymentTester();
        ContainerImageExpirer expirer = new ContainerImageExpirer(tester.controller(), Duration.ofDays(1));
        ContainerRegistryMock registry = tester.controllerTester().serviceRegistry().containerRegistry();

        Instant instant = tester.clock().instant();
        ContainerImage image0 = new ContainerImage("image0", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.1"));
        ContainerImage image1 = new ContainerImage("image1", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.2"));
        ContainerImage image2 = new ContainerImage("image2", "registry.example.com", "vespa/vespa", instant, Version.fromString("7.4"));
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
        ContainerImage image3 = new ContainerImage("image3", "registry.example.com", "vespa/vespa", tester.clock().instant(), Version.fromString("7.3"));
        registry.add(image3);
        tester.controllerTester().upgradeSystem(image3.version());
        expirer.maintain();
        assertEquals(List.of(image3, image2), registry.list());
    }

}
