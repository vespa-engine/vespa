// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class NodeRepoStatsTest {

    private static final double delta = 0.0001;

    @Test
    public void testEmpty() {
        var tester = new NodeRepositoryTester();
        var stats = tester.nodeRepository().computeStats();

        assertEquals(0, stats.totalCost(), delta);
        assertEquals(0, stats.totalAllocatedCost(), delta);
        assertLoad(Load.zero(), stats.load());
        assertLoad(Load.zero(), stats.activeLoad());
        assertTrue(stats.applicationStats().isEmpty());
    }

    @Test
    public void testHostButNoNodes() {
        var tester = new NodeRepositoryTester();
        tester.addHost("host1", "default");
        tester.addHost("host2", "default");
        tester.addHost("host3", "small");
        var stats = tester.nodeRepository().computeStats();

        assertEquals(0.76, stats.totalCost(), delta);
        assertEquals(0, stats.totalAllocatedCost(), delta);
        assertLoad(Load.zero(), stats.load());
        assertLoad(Load.zero(), stats.activeLoad());
        assertTrue(stats.applicationStats().isEmpty());
    }

    @Test
    public void testStats() {
        var hostResources = new NodeResources(10, 100, 1000, 10,
                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local);

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(10, hostResources).activateTenantHosts();

        var app1 = ProvisioningTester.applicationId("app1");
        var app2 = ProvisioningTester.applicationId("app2");
        var app3 = ProvisioningTester.applicationId("app3");
        var cluster1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("cluster1")).vespaVersion(Version.fromString("7")).build();
        var cluster2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("cluster2")).vespaVersion(Version.fromString("7")).build();
        var small = new NodeResources(1, 10, 100, 1, NodeResources.DiskSpeed.any);
        var large = new NodeResources(2, 20, 200, 1);

        // Deploy apps
        var hostsApp1 = tester.prepare(app1, cluster1, Capacity.from(new ClusterResources(6, 1, small)));
        hostsApp1.addAll(tester.prepare(app1, cluster2, Capacity.from(new ClusterResources(4, 1, large))));
        tester.activate(app1, hostsApp1);
        tester.activate(app2, cluster1, Capacity.from(new ClusterResources(8, 1, small)));
        tester.activate(app3, cluster1, Capacity.from(new ClusterResources(5, 1, large)));

        // Add metrics
        double loadApp1Cluster1 = 0.2;
        double loadApp1Cluster2 = 0.3;
        double loadApp2 = 0.4;
        double loadApp3 = 0.5;
        var now = tester.clock().instant();
        for (Node node : tester.nodeRepository().nodes().list(Node.State.active)) {
            double loadFactor;
            var allocation = node.allocation().get();
            if (allocation.owner().equals(app1)) {
                if (allocation.membership().cluster().id().equals(cluster1.id()))
                    loadFactor = loadApp1Cluster1;
                else
                    loadFactor = loadApp1Cluster2;
            }
            else if (allocation.owner().equals(app2)) {
                loadFactor = loadApp2;
            }
            else {
                loadFactor = loadApp3;
            }
            var snapshot = new NodeMetricSnapshot(now, new Load(1.0, 0.9, 0.8).multiply(loadFactor), 1, true, true, 1.0 );
            tester.nodeRepository().metricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(), snapshot)));
        }

        var stats = tester.nodeRepository().computeStats();

        assertEquals(26, stats.totalCost(), delta);
        assertEquals(8.319999999999999, stats.totalAllocatedCost(), delta);

        assertLoad(new Load(0.6180,0.5562,0.4944), stats.load());
        assertLoad(new Load(0.4682,0.4214,0.3745), stats.activeLoad());

        var app1Stats = stats.applicationStats().get(0);
        var app2Stats = stats.applicationStats().get(2);
        var app3Stats = stats.applicationStats().get(1);

        assertEquals(app1, app1Stats.id());
        assertEquals(3.6400, app1Stats.cost(), delta);
        assertEquals(0.8676, app1Stats.utilizedCost(), delta);
        assertEquals(2.7724, app1Stats.unutilizedCost(), delta);
        assertLoad(new Load(0.2571, 0.2314, 0.2057), app1Stats.load());

        assertEquals(app2, app2Stats.id());
        assertEquals(2.0799, app2Stats.cost(), delta);
        assertEquals(0.7712, app2Stats.utilizedCost(), delta);
        assertEquals(1.3087, app2Stats.unutilizedCost(), delta);
        assertLoad(new Load(.40, 0.36, 0.32), app2Stats.load());

        assertEquals(app3, app3Stats.id());
        assertEquals(2.6000, app3Stats.cost(), delta);
        assertEquals(1.2049, app3Stats.utilizedCost(), delta);
        assertEquals(1.3950, app3Stats.unutilizedCost(), delta);
        assertLoad(new Load(0.5, 0.45, 0.40), app3Stats.load());
    }

    private static void assertLoad(Load expected, Load actual) {
        assertEquals("cpu",    expected.cpu(), actual.cpu(), delta);
        assertEquals("memory", expected.memory(), actual.memory(), delta);
        assertEquals("disk",   expected.disk(), actual.disk(), delta);
    }

}
