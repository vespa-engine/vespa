// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class DockerImagesTest {

    @Test
    public void image_selection() {
        var flagSource = new InMemoryFlagSource();
        var tester = new ProvisioningTester.Builder().flagSource(flagSource).build();

        // Host uses tenant default image (for preload purposes)
        var defaultImage = DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa");
        var hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        tester.deployZoneApp();
        for (var host : hosts) {
            assertEquals(defaultImage, tester.nodeRepository().dockerImages().dockerImageFor(host.type()));
        }

        // Tenant node uses tenant default image
        var resources = new NodeResources(2, 8, 50, 1);
        for (var host : hosts) {
            var nodes = tester.makeReadyVirtualDockerNodes(2, resources, host.hostname());
            for (var node : nodes) {
                assertEquals(defaultImage, tester.nodeRepository().dockerImages().dockerImageFor(node.type()));
            }
        }
    }

}
