// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * tests basic operation of the node repository
 * 
 * @author bratseth
 */
public class NodeRepositoryTest {

    @Test
    public void add_and_remove() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        assertEquals(0, tester.nodeRepository().nodes().list().size());

        tester.addHost("id1", "host1", "default", NodeType.host);
        tester.addHost("id2", "host2", "default", NodeType.host);
        tester.addHost("id3", "host3", "default", NodeType.host);
        tester.addHost("id4", "cfghost1", "default", NodeType.confighost);

        assertEquals(4, tester.nodeRepository().nodes().list().size());

        for (var hostname : List.of("host2", "cfghost1")) {
            tester.nodeRepository().nodes().park(hostname, false, Agent.system, "Parking to unit test");
            tester.nodeRepository().nodes().removeRecursively(hostname);
        }

        assertEquals(4, tester.nodeRepository().nodes().list().size());
        assertEquals(2, tester.nodeRepository().nodes().list(Node.State.deprovisioned).size());
    }

    @Test
    public void only_allow_docker_containers_remove_in_ready() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.addHost("id1", "host1", "docker", NodeType.tenant);

        try {
            tester.nodeRepository().nodes().removeRecursively("host1"); // host1 is in state provisioned
            fail("Should not be able to delete docker container node by itself in state provisioned");
        } catch (IllegalArgumentException ignored) {
            // Expected
        }

        tester.nodeRepository().nodes().setReady(tester.nodeRepository().nodes().lockAndGetRequired("host1"), Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().removeRecursively("host1");
    }

    @Test
    public void only_remove_tenant_docker_containers_for_new_allocations() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.addHost("host1", "host1", "default", NodeType.host);
        tester.addHost("tenant1", "tenant1", "docker", NodeType.tenant);
        tester.addHost("cfg1", "cfg1", "docker", NodeType.config);

        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation("host1", Agent.system, getClass().getSimpleName());
        assertEquals(Node.State.ready, tester.nodeRepository().nodes().node("host1").get().state());

        tester.nodeRepository().nodes().deallocateRecursively("tenant1", Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation("tenant1", Agent.system, getClass().getSimpleName());
        assertFalse(tester.nodeRepository().nodes().node("tenant1").isPresent());

        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation("cfg1", Agent.system, getClass().getSimpleName());
        assertEquals(Node.State.ready, tester.nodeRepository().nodes().node("cfg1").get().state());
    }

    @Test
    public void fail_readying_with_hard_fail() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.addHost("host1", "host1", "default", NodeType.host);
        tester.addHost("host2", "host2", "default", NodeType.host);

        Node node2 = tester.nodeRepository().nodes().node("host2").orElseThrow();
        var reportsBuilder = new Reports.Builder(node2.reports());
        reportsBuilder.setReport(Report.basicReport("reportId", Report.Type.HARD_FAIL, Instant.EPOCH, "hardware failure"));
        node2 = node2.with(reportsBuilder.build());
        tester.nodeRepository().nodes().write(node2, () -> {});

        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation("host1", Agent.system, getClass().getSimpleName());
        assertEquals(Node.State.ready, tester.nodeRepository().nodes().node("host1").get().state());

        try {
            tester.nodeRepository().nodes().markNodeAvailableForNewAllocation("host2", Agent.system, getClass().getSimpleName());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("hardware failure"));
        }
    }

    @Test
    public void delete_host_only_after_all_the_children_have_been_deleted() {
        NodeRepositoryTester tester = new NodeRepositoryTester();

        tester.addHost("id1", "host1", "default", NodeType.host);
        tester.addHost("id2", "host2", "default", NodeType.host);
        tester.addNode("node10", "node10", "host1", "docker", NodeType.tenant);
        tester.addNode("node11", "node11", "host1", "docker", NodeType.tenant);
        tester.addNode("node12", "node12", "host1", "docker", NodeType.tenant);
        tester.addNode("node20", "node20", "host2", "docker", NodeType.tenant);
        tester.setNodeState("node11", Node.State.active);
        assertEquals(6, tester.nodeRepository().nodes().list().size());

        try {
            tester.nodeRepository().nodes().removeRecursively("host1");
            fail("Should not be able to delete host node, one of the children is in state active");
        } catch (IllegalArgumentException ignored) {
            // Expected
        }
        assertEquals(6, tester.nodeRepository().nodes().list().size());

        // Should be OK to delete host2 as both host2 and its only child, node20, are in state provisioned
        tester.nodeRepository().nodes().removeRecursively("host2");
        assertEquals(5, tester.nodeRepository().nodes().list().size());
        assertEquals(Node.State.deprovisioned, tester.nodeRepository().nodes().node("host2").get().state());

        // Now node10 is in provisioned, set node11 to failed and node12 to ready, and it should be OK to delete host1
        tester.nodeRepository().nodes().fail("node11", Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().setReady(tester.nodeRepository().nodes().lockAndGetRequired("node12"), Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().removeRecursively("node12"); // Remove one of the children first instead
        assertEquals(4, tester.nodeRepository().nodes().list().size());
        tester.nodeRepository().nodes().removeRecursively("host1");
        assertEquals(Node.State.deprovisioned, tester.nodeRepository().nodes().node("host1").get().state());
        assertEquals(IP.Config.EMPTY.primary(), tester.nodeRepository().nodes().node("host1").get().ipConfig().primary());
    }

    @Test
    public void delete_config_host() {
        NodeRepositoryTester tester = new NodeRepositoryTester();

        String cfghost1 = "cfghost1";
        String cfg1 = "cfg1";
        tester.addHost("id1", cfghost1, "default", NodeType.confighost);
        tester.addNode("id2", cfg1, cfghost1, "docker", NodeType.config);
        tester.setNodeState(cfghost1, Node.State.active);
        tester.setNodeState(cfg1, Node.State.active);
        assertEquals(2, tester.nodeRepository().nodes().list().size());

        try {
            tester.nodeRepository().nodes().removeRecursively(cfghost1);
            fail("Should not be able to delete host node, one of the children is in state active");
        } catch (IllegalArgumentException ignored) { }
        assertEquals(2, tester.nodeRepository().nodes().list().size());

        // Fail host and container
        tester.nodeRepository().nodes().failOrMarkRecursively(cfghost1, Agent.system, getClass().getSimpleName());

        assertEquals("cfg1 is not failed yet as it active",
                     Node.State.active, tester.nodeRepository().nodes().node(cfg1).get().state());
        assertEquals("cfghost1 is not failed yet as it active",
                     Node.State.active, tester.nodeRepository().nodes().node(cfghost1).get().state());
        assertTrue(tester.nodeRepository().nodes().node(cfg1).get().status().wantToFail());
        assertTrue(tester.nodeRepository().nodes().node(cfghost1).get().status().wantToFail());

        tester.nodeRepository().nodes().fail(cfg1, Agent.system, "test");
        tester.nodeRepository().nodes().fail(cfghost1, Agent.system, "test");

        // Remove recursively
        tester.nodeRepository().nodes().removeRecursively(cfghost1);
        assertEquals(0, tester.nodeRepository().nodes().list().not().state(Node.State.deprovisioned).size());
    }

    @Test
    public void relevant_information_from_deprovisioned_hosts_are_merged_into_readded_host() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        Instant testStart = tester.nodeRepository().clock().instant();

        tester.clock().advance(Duration.ofSeconds(1));
        tester.addHost("id1", "host1", "default", NodeType.host);
        tester.addHost("id2", "host2", "default", NodeType.host);
        assertFalse(tester.nodeRepository().nodes().node("host1").get().history().hasEventAfter(History.Event.Type.deprovisioned, testStart));

        // Set host 1 properties and deprovision it
        try (var lock = tester.nodeRepository().nodes().lockAndGetRequired("host1")) {
            Node host1 = lock.node().withWantToRetire(true, true, Agent.system, tester.nodeRepository().clock().instant());
            host1 = host1.withFirmwareVerifiedAt(tester.clock().instant());
            host1 = host1.with(host1.status().withIncreasedFailCount());
            host1 = host1.with(host1.reports().withReport(Report.basicReport("id", Report.Type.HARD_FAIL, tester.clock().instant(), "Test report")));
            tester.nodeRepository().nodes().write(host1, lock);
        }
        tester.nodeRepository().nodes().removeRecursively("host1");

        // Set host 2 properties and deprovision it
        try (var lock = tester.nodeRepository().nodes().lockAndGetRequired("host2")) {
            Node host2 = lock.node().withWantToRetire(true, false, true, Agent.system, tester.nodeRepository().clock().instant());
            tester.nodeRepository().nodes().write(host2, lock);
        }
        tester.nodeRepository().nodes().removeRecursively("host2");

        // Host 1 is deprovisioned and unwanted properties are cleared
        Node host1 = tester.nodeRepository().nodes().node("host1").get();
        Node host2 = tester.nodeRepository().nodes().node("host2").get();
        assertEquals(Node.State.deprovisioned, host1.state());
        assertTrue(host1.history().hasEventAfter(History.Event.Type.deprovisioned, testStart));

        // Adding it again preserves some information from the deprovisioned host and removes it
        tester.addHost("id2", "host1", "default", NodeType.host);
        host1 = tester.nodeRepository().nodes().node("host1").get();
        assertEquals("This is the newly added node", "id2", host1.id());
        assertFalse("The old 'host1' is removed",
                    tester.nodeRepository().nodes().node("host1", Node.State.deprovisioned).isPresent());
        assertFalse("Not transferred from deprovisioned host", host1.status().wantToRetire());
        assertFalse("Not transferred from deprovisioned host", host1.status().wantToDeprovision());
        assertTrue("Transferred from deprovisioned host", host1.history().hasEventAfter(History.Event.Type.deprovisioned, testStart));
        assertTrue("Transferred from deprovisioned host", host1.status().firmwareVerifiedAt().isPresent());
        assertEquals("Transferred from deprovisioned host", 1, host1.status().failCount());
        assertEquals("Transferred from deprovisioned host", 1, host1.reports().getReports().size());
        assertTrue("Transferred from rebuilt host", host2.status().wantToRetire());
        assertTrue("Transferred from rebuilt host", host2.status().wantToRebuild());
    }

    @Test
    public void dirty_host_only_if_we_can_dirty_children() {
        NodeRepositoryTester tester = new NodeRepositoryTester();

        tester.addHost("id1", "host1", "default", NodeType.host);
        tester.addHost("id2", "host2", "default", NodeType.host);
        tester.addNode("node10", "node10", "host1", "docker", NodeType.tenant);
        tester.addNode("node11", "node11", "host1", "docker", NodeType.tenant);
        tester.addNode("node12", "node12", "host1", "docker", NodeType.tenant);
        tester.addNode("node20", "node20", "host2", "docker", NodeType.tenant);

        tester.setNodeState("node11", Node.State.ready);
        tester.setNodeState("node12", Node.State.active);
        tester.setNodeState("node20", Node.State.failed);

        assertEquals(6, tester.nodeRepository().nodes().list().size());

        // Should be OK to dirty host2 as it is in provisioned and its only child is in failed
        tester.nodeRepository().nodes().deallocateRecursively("host2", Agent.system, NodeRepositoryTest.class.getSimpleName());
        assertEquals(Set.of("host2", "node20"), filterNodes(tester, node -> node.state() == Node.State.dirty));

        // Cant dirty host1, node11 is ready and node12 is active
        try {
            tester.nodeRepository().nodes().deallocateRecursively("host1", Agent.system, NodeRepositoryTest.class.getSimpleName());
            fail("Should not be able to dirty host1");
        } catch (IllegalArgumentException ignored) { } // Expected;

        assertEquals(Set.of("host2", "node20"), filterNodes(tester, node -> node.state() == Node.State.dirty));
    }

    @Test
    public void breakfix_tenant_host() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        tester.addHost("host1", "host1", "default", NodeType.host);
        tester.addNode("node1", "node1", "host1", "docker", NodeType.tenant);
        String reason = NodeRepositoryTest.class.getSimpleName();

        try {
            tester.nodeRepository().nodes().breakfixRecursively("node1", Agent.system, reason);
            fail("Should not be able to breakfix tenant node");
        } catch (IllegalArgumentException ignored) {}

        try {
            tester.nodeRepository().nodes().breakfixRecursively("host1", Agent.system, reason);
            fail("Should not be able to breakfix host in state not in [parked, failed]");
        } catch (IllegalArgumentException ignored) {}

        tester.setNodeState("host1", Node.State.failed);
        tester.setNodeState("node1", Node.State.active);
        try {
            tester.nodeRepository().nodes().breakfixRecursively("host1", Agent.system, reason);
            fail("Should not be able to breakfix host with active tenant node");
        } catch (IllegalArgumentException ignored) {}

        tester.setNodeState("node1", Node.State.failed);
        tester.nodeRepository().nodes().breakfixRecursively("host1", Agent.system, reason);

        assertEquals(1, tester.nodeRepository().nodes().list().size());
        Node node = tester.nodeRepository().nodes().list().first().get();
        assertEquals("host1", node.hostname());
        assertEquals(Node.State.breakfixed, node.state());
    }

    private static Set<String> filterNodes(NodeRepositoryTester tester, Predicate<Node> filter) {
        return tester.nodeRepository().nodes().list().matching(filter).hostnames();
    }

}
