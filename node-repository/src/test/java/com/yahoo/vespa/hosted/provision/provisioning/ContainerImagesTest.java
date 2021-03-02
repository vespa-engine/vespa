// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ContainerImagesTest {

    @Test
    public void image_selection() {
        var flagSource = new InMemoryFlagSource();
        var tester = new ProvisioningTester.Builder().flagSource(flagSource).build();

        var proxyImage = DockerImage.fromString("docker-registry.domain.tld:8080/dist/proxy");
        tester.nodeRepository().containerImages().setImage(NodeType.proxy, Optional.of(proxyImage));

        // Host uses tenant default image (for preload purposes)
        var defaultImage = DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa");
        var hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        tester.activateTenantHosts();
        for (var host : hosts) {
            assertEquals(defaultImage, tester.nodeRepository().containerImages().imageFor(host.type()));
        }

        // Tenant node uses tenant default image
        var resources = new NodeResources(2, 8, 50, 1);
        for (var host : hosts) {
            var nodes = tester.makeReadyChildren(2, resources, host.hostname());
            for (var node : nodes) {
                assertEquals(defaultImage, tester.nodeRepository().containerImages().imageFor(node.type()));
            }
        }

        // Proxy host uses image used by child nodes (proxy nodes), which is overridden in this case (for preload purposes)
        var proxyHosts = tester.makeReadyNodes(2, "default", NodeType.proxyhost);
        for (var host : proxyHosts) {
            assertEquals(proxyImage, tester.nodeRepository().containerImages().imageFor(host.type()));
        }
    }

    @Test
    public void image_replacement() {
        var flagSource = new InMemoryFlagSource();
        var defaultImage = DockerImage.fromString("foo.example.com/vespa/vespa");
        var tester = new ProvisioningTester.Builder().defaultImage(defaultImage).flagSource(flagSource).build();
        var hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        tester.activateTenantHosts();

        // Default image is used when there is no replacement
        for (var host : hosts) {
            assertEquals(defaultImage, tester.nodeRepository().containerImages().imageFor(host.type()));
        }

        // Replacement image is preferred
        DockerImage imageWithReplacement = defaultImage.withReplacedBy(DockerImage.fromString("bar.example.com/vespa/vespa"));
        tester = new ProvisioningTester.Builder().defaultImage(imageWithReplacement).flagSource(flagSource).build();
        hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        for (var host : hosts) {
            assertEquals(imageWithReplacement.replacedBy().get().asString(),
                         tester.nodeRepository().containerImages().imageFor(host.type()).asString());
        }
    }

}
