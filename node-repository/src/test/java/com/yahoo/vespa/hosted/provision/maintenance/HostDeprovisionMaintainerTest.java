// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.createNode;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.proxyApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.proxyHostApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.tenantApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.tenantHostApp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author freva
 */
public class HostDeprovisionMaintainerTest {
    private final HostProvisionerTester tester = new HostProvisionerTester();
    private final HostProvisioner hostProvisioner = mock(HostProvisioner.class);
    private final FlagSource flagSource = new InMemoryFlagSource().withBooleanFlag(Flags.ENABLE_DYNAMIC_PROVISIONING.id(), true);
    private final HostDeprovisionMaintainer maintainer = new HostDeprovisionMaintainer(
            tester.nodeRepository(), Duration.ofDays(1), tester.jobControl(), hostProvisioner, flagSource);

    @Test
    public void removes_nodes_if_successful() {
        tester.addNode("host1", Optional.empty(), NodeType.host, Node.State.active, Optional.of(tenantHostApp));
        tester.addNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));
        tester.addNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, Optional.empty());
        tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());
        tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));

        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp));
        tester.addNode("host2-1", Optional.of("host2"), NodeType.tenant, Node.State.failed, Optional.empty());

        assertEquals(7, tester.nodeRepository().getNodes().size());

        maintainer.maintain();

        assertEquals(5, tester.nodeRepository().getNodes().size());
        verify(hostProvisioner).deprovision(eq(host2));
        verifyNoMoreInteractions(hostProvisioner);

        assertTrue(tester.nodeRepository().getNode("host2").isEmpty());
        assertTrue(tester.nodeRepository().getNode("host2-1").isEmpty());
    }

    @Test
    public void does_not_remove_if_failed() {
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp));
        doThrow(new RuntimeException()).when(hostProvisioner).deprovision(eq(host2));

        maintainer.maintain();

        assertEquals(1, tester.nodeRepository().getNodes().size());
        verify(hostProvisioner).deprovision(eq(host2));
        verifyNoMoreInteractions(hostProvisioner);
    }

    @Test
    public void finds_nodes_that_need_deprovisioning() {
        Node host2 = createNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp));
        Node host21 = createNode("host2-1", Optional.of("host2"), NodeType.tenant, Node.State.failed, Optional.empty());
        Node host3 = createNode("host3", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());

        List<Node> nodes = List.of(
                createNode("host1", Optional.empty(), NodeType.host, Node.State.active, Optional.of(tenantHostApp)),
                createNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp)),
                createNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, Optional.empty()),

                host2, host21, host3,

                createNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty()),
                createNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp)),

                createNode("proxyhost1", Optional.empty(), NodeType.proxyhost, Node.State.provisioned, Optional.empty()),

                createNode("proxyhost2", Optional.empty(), NodeType.proxyhost, Node.State.active, Optional.of(proxyHostApp)),
                createNode("proxy2", Optional.of("proxyhost2"), NodeType.proxy, Node.State.active, Optional.of(proxyApp)));

        Set<Node> expected = Set.of(host2, host3);
        Set<Node> actual = HostDeprovisionMaintainer.candidates(new NodeList(nodes));
        assertEquals(expected, actual);
    }
}