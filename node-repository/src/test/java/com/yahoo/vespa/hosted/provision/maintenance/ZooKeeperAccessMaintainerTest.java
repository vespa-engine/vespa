// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ZooKeeperAccessMaintainerTest {

    @Test
    public void test() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.curator().setZooKeeperEnsembleConnectionSpec("server1:1234,server2:5678");
        ZooKeeperAccessMaintainer maintainer = new ZooKeeperAccessMaintainer(tester.nodeRepository(), 
                                                                             tester.curator(), Duration.ofHours(1), new JobControl(tester.nodeRepository().database()));
        assertTrue(ZooKeeperServer.getAllowedClientHostnames().isEmpty());
        maintainer.maintain();
        assertTrue("We don't restrict to only config servers", ZooKeeperServer.getAllowedClientHostnames().isEmpty());

        tester.addNode("id1", "host1", "default", NodeType.tenant);
        tester.addNode("id2", "host2", "default", NodeType.tenant);
        tester.addNode("id3", "host3", "default", NodeType.tenant);
        maintainer.maintain();

        assertEquals(3, tester.getNodes(NodeType.tenant).size());
        assertEquals(0, tester.getNodes(NodeType.proxy).size());
        assertEquals(asSet("host1,host2,host3,server1,server2"), ZooKeeperServer.getAllowedClientHostnames());

        tester.addNode("proxy1", "host4", "default", NodeType.proxy);
        tester.addNode("proxy2", "host5", "default", NodeType.proxy);
        maintainer.maintain();

        assertEquals(3, tester.getNodes(NodeType.tenant).size());
        assertEquals(2, tester.getNodes(NodeType.proxy).size());
        assertEquals(asSet("host1,host2,host3,host4,host5,server1,server2"), ZooKeeperServer.getAllowedClientHostnames());

        tester.nodeRepository().park("host2", Agent.system, "Parking to unit test");
        tester.nodeRepository().removeRecursively("host2");
        maintainer.maintain();

        assertEquals(2, tester.getNodes(NodeType.tenant).size());
        assertEquals(2, tester.getNodes(NodeType.proxy).size());
        assertEquals(asSet("host1,host3,host4,host5,server1,server2"), ZooKeeperServer.getAllowedClientHostnames());

        tester.addNode("docker-host-1", "host6", "default", NodeType.host);
        tester.addNode("docker-host-2", "host7", "default", NodeType.host);
        maintainer.maintain();
        assertEquals(asSet("host1,host3,host4,host5,host6,host7,server1,server2"), ZooKeeperServer.getAllowedClientHostnames());
    }

    private Set<String> asSet(String s) {
        return new HashSet<>(Arrays.asList(s.split(",")));
    }

}
