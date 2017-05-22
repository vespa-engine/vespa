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
        assertEquals(0, tester.getNodes(NodeType.tenant).size());

        tester.addNode("id1", "host1", "default", NodeType.tenant);
        tester.addNode("id2", "host2", "default", NodeType.tenant);
        tester.addNode("id3", "host3", "default", NodeType.tenant);

        assertEquals(3, tester.getNodes(NodeType.tenant).size());
        
        tester.nodeRepository().park("host2", Agent.system, "Parking to unit test");
        assertTrue(tester.nodeRepository().remove("host2"));

        assertEquals(2, tester.getNodes(NodeType.tenant).size());
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
    public void featureToggleDynamicAllocationTest() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        assertFalse(tester.nodeRepository().dynamicAllocationEnabled());

        tester.curator().set(Path.fromString("/provision/v1/dynamicDockerAllocation"), new byte[0]);
        assertTrue(tester.nodeRepository().dynamicAllocationEnabled());
    }
}
