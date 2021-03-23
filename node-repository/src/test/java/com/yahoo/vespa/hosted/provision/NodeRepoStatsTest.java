// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeRepoStatsTest {

    private static final double delta = 0.0001;

    @Test
    public void testEmpty() {
        var tester = new NodeRepositoryTester();
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().load());
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().activeLoad());
    }

    @Test
    public void testHostButNoNodes() {
        var tester = new NodeRepositoryTester();
        tester.addHost("host1", "default");
        tester.addHost("host2", "default");
        tester.addHost("host3", "small");
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().load());
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().activeLoad());
    }

    @Test
    public void testNodesAndMetrics() {
        var tester = new NodeRepositoryTester();
        tester.addHost("host1", "default");
        tester.addHost("host2", "default");
        tester.addHost("host3", "small");
        tester.addNode("node1", "host1", new NodeResources(0.2, 0.5, 4, 1));
        tester.addNode("node2", "host1", new NodeResources(0.3, 1.0, 8, 1));
        tester.addNode("node3", "host3", new NodeResources(0.3, 1.5, 12, 1));
        tester.setNodeState("node1", Node.State.active);
        tester.setNodeState("node2", Node.State.active);
        tester.setNodeState("node3", Node.State.active);
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().load());
        assertLoad(Load.zero(), tester.nodeRepository().computeStats().activeLoad());

        var before = tester.clock().instant();
        tester.clock().advance(Duration.ofMinutes(5));
        var now = tester.clock().instant();
        tester.nodeRepository().metricsDb().addNodeMetrics(
                List.of(new Pair<>("node1", new NodeMetricSnapshot(before, 0, 0, 0, 1,  true, true, 1.0)),
                        new Pair<>("node1", new NodeMetricSnapshot(now, 0.5, 0.1, 0.8, 1,  true, true, 1.0)),
                        new Pair<>("node2", new NodeMetricSnapshot(now, 0.1, 0.8, 0.1, 1,  true, true, 1.0)),
                        new Pair<>("node3", new NodeMetricSnapshot(now, 1.0, 0.1, 0.2, 1,  true, true, 1.0))));
        assertLoad(new Load(0.0860, 0.1000, 0.0256), tester.nodeRepository().computeStats().load());
        assertLoad(new Load(0.5375, 0.3333, 0.2667), tester.nodeRepository().computeStats().activeLoad());
    }

    private static void assertLoad(Load expected, Load actual) {
        assertEquals("cpu",    expected.cpu(), actual.cpu(), delta);
        assertEquals("memory", expected.memory(), actual.memory(), delta);
        assertEquals("disk",   expected.disk(), actual.disk(), delta);
    }

}
