// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Tests deployment to docker images which share the same physical host.
 *
 * @author bratseth
 */
public class DockerProvisioningTest {
    private static final String dockerFlavor = "docker1";

    @Test
    public void docker_application_deployment() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        ApplicationId application1 = tester.makeApplicationId();

        for (int i = 1; i < 10; i++) {
            tester.makeReadyDockerNodes(1, dockerFlavor, "dockerHost" + i);
        }

        List<HostSpec> hosts = tester.prepare(application1, ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Optional.empty()), 7, 1, dockerFlavor);
        tester.activate(application1, new HashSet<>(hosts));

        final NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(7, nodes.size());
        assertEquals(dockerFlavor, nodes.asList().get(0).flavor().canonicalName());
    }

    // In dev, test and staging you get nodes with default flavor, but we should get specified flavor for docker nodes
    @Test
    public void get_specified_flavor_not_default_flavor_for_docker() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.test, RegionName.from("corp-us-east-1")));
        ApplicationId application1 = tester.makeApplicationId();
        tester.makeReadyDockerNodes(1, dockerFlavor, "dockerHost");

        List<HostSpec> hosts = tester.prepare(application1, ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Optional.empty()), 1, 1, dockerFlavor);
        tester.activate(application1, new HashSet<>(hosts));

        final NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(1, nodes.size());
        assertEquals(dockerFlavor, nodes.asList().get(0).flavor().canonicalName());
    }

}
