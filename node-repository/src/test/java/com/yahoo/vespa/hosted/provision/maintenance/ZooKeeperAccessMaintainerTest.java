package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * @author bratseth
 */
public class ZooKeeperAccessMaintainerTest {

    @Test
    public void test() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.curator().setConnectionSpec("server1:1234,server2:5678");
        ZooKeeperAccessMaintainer maintainer = new ZooKeeperAccessMaintainer(tester.nodeRepository(), 
                                                                             tester.curator(), Duration.ofHours(1));
        assertNull(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY));
        maintainer.maintain();
        assertEquals(asSet("server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));

        tester.addNode("id1", "host1", "default", Node.Type.tenant);
        tester.addNode("id2", "host2", "default", Node.Type.tenant);
        tester.addNode("id3", "host3", "default", Node.Type.tenant);
        maintainer.maintain();

        assertEquals(3, tester.getNodes(Node.Type.tenant).size());
        assertEquals(asSet("host1,host2,host3,server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));

        tester.addNode("proxy1", "host4", "default", Node.Type.proxy);
        tester.addNode("proxy2", "host5", "default", Node.Type.proxy);
        maintainer.maintain();

        assertEquals(3, tester.getNodes(Node.Type.tenant).size());
        assertEquals(asSet("host1,host2,host3,host4,host5,server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));

        tester.nodeRepository().move("host2", Node.State.parked);
        assertTrue(tester.nodeRepository().remove("host2"));
        maintainer.maintain();

        assertEquals(2, tester.getNodes(Node.Type.tenant).size());
        assertEquals(asSet("host1,host3,host4,host5,server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));
    }

    private Set<String> asSet(String s) {
        return new HashSet<>(Arrays.asList(s.split(",")));
    }

}
