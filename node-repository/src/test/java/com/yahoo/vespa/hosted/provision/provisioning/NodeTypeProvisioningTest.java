package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests provisioning by node type instead of by count and flavor
 * 
 * @author bratseth
 */
public class NodeTypeProvisioningTest {

    @Test
    public void proxy_deployment() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

        tester.makeReadyNodes( 1, "small", NodeType.proxy);
        tester.makeReadyNodes( 3, "small", NodeType.host);
        tester.makeReadyNodes( 5, "small", NodeType.tenant);
        tester.makeReadyNodes(10, "large", NodeType.proxy);
        tester.makeReadyNodes(20, "large", NodeType.host);
        tester.makeReadyNodes(40, "large", NodeType.tenant);

        ApplicationId application = tester.makeApplicationId(); // application using proxy nodes

        
        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals("Activated all proxies", 11, nodes.size());
            for (Node node : nodes)
                assertEquals(NodeType.proxy, node.type());
        }

        { // Redeploy with no changes
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());
        }

        { // Add 2 ready proxies then redeploy
            tester.makeReadyNodes(2, "small", NodeType.proxy);
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(13, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(13, nodes.size());
        }

        { // Remove 3 proxies then redeploy
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            tester.nodeRepository().fail(nodes.get(0).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().fail(nodes.get(1).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().fail(nodes.get(5).hostname(), Agent.system, "Failing to unit test");
            
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(10, nodes.size());
        }
    }

    private List<HostSpec> deployProxies(ApplicationId application, ProvisioningTester tester) {
        return tester.prepare(application, 
                              ClusterSpec.requestVersion(ClusterSpec.Type.container,
                                                         ClusterSpec.Id.from("test"),
                                                         Optional.empty()),
                              Capacity.fromRequiredNodeType(NodeType.proxy),
                              1);
        
    }

}
