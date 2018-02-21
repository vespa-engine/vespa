// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * tests basic operation of the node repository
 * 
 * @author bratseth
 */
public class NodeRepositoryTest {

    @Test
    public void nodeRepositoryTest() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        assertEquals(0, tester.nodeRepository().getNodes().size());

        tester.addNode("id1", "host1", "default", NodeType.tenant);
        tester.addNode("id2", "host2", "default", NodeType.tenant);
        tester.addNode("id3", "host3", "default", NodeType.tenant);

        assertEquals(3, tester.nodeRepository().getNodes().size());
        
        tester.nodeRepository().park("host2", Agent.system, "Parking to unit test");
        tester.nodeRepository().removeRecursively("host2");

        assertEquals(2, tester.nodeRepository().getNodes().size());
    }

    @Test
    public void applicationDefaultFlavor() {
        NodeRepositoryTester tester = new NodeRepositoryTester();

        ApplicationId application = ApplicationId.from(TenantName.from("a"), ApplicationName.from("b"), InstanceName.from("c"));

        Path path = Path.fromString("/provision/v1/defaultFlavor").append(application.serializedForm());
        String flavor = "example-flavor";
        tester.curator().create(path);
        tester.curator().set(path, flavor.getBytes(StandardCharsets.UTF_8));

        assertEquals(Optional.of(flavor), tester.nodeRepository().getDefaultFlavorOverride(application));

        ApplicationId applicationWithoutDefaultFlavor =
                ApplicationId.from(TenantName.from("does"), ApplicationName.from("not"), InstanceName.from("exist"));
        assertFalse(tester.nodeRepository().getDefaultFlavorOverride(applicationWithoutDefaultFlavor).isPresent());
    }

    @Test
    public void only_allow_docker_containers_remove_in_ready() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.addNode("id1", "host1", "docker", NodeType.tenant);

        try {
            tester.nodeRepository().removeRecursively("host1"); // host1 is in state provisioned
            fail("Should not be able to delete docker container node by itself in state provisioned");
        } catch (IllegalArgumentException ignored) {
            // Expected
        }

        tester.nodeRepository().setDirty("host1", Agent.system, getClass().getSimpleName());
        tester.nodeRepository().setReady("host1", Agent.system, getClass().getSimpleName());
        tester.nodeRepository().removeRecursively("host1");
    }

    @Test
    public void delete_host_only_after_all_the_children_have_been_deleted() {
        NodeRepositoryTester tester = new NodeRepositoryTester();

        tester.addNode("id1", "host1", "default", NodeType.host);
        tester.addNode("id2", "host2", "default", NodeType.host);
        tester.addNode("node10", "node10", "host1", "docker", NodeType.tenant);
        tester.addNode("node11", "node11", "host1", "docker", NodeType.tenant);
        tester.addNode("node12", "node12", "host1", "docker", NodeType.tenant);
        tester.addNode("node20", "node20", "host2", "docker", NodeType.tenant);
        assertEquals(6, tester.nodeRepository().getNodes().size());

        tester.nodeRepository().setDirty("node11", Agent.system, getClass().getSimpleName());

        try {
            tester.nodeRepository().removeRecursively("host1");
            fail("Should not be able to delete host node, one of the children is in state dirty");
        } catch (IllegalArgumentException ignored) {
            // Expected
        }
        assertEquals(6, tester.nodeRepository().getNodes().size());

        // Should be OK to delete host2 as both host2 and its only child, node20, are in state provisioned
        tester.nodeRepository().removeRecursively("host2");
        assertEquals(4, tester.nodeRepository().getNodes().size());

        // Now node10 and node12 are in provisioned, set node11 to ready, and it should be OK to delete host1
        tester.nodeRepository().setReady("node11", Agent.system, getClass().getSimpleName());
        tester.nodeRepository().removeRecursively("node11"); // Remove one of the children first instead
        assertEquals(3, tester.nodeRepository().getNodes().size());

        tester.nodeRepository().removeRecursively("host1");
        assertEquals(0, tester.nodeRepository().getNodes().size());
    }
}
