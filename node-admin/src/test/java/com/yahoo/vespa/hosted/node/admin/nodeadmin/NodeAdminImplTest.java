// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.config.provision.NodeType;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextFactory;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl.NodeAgentWithScheduler;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl.NodeAgentWithSchedulerFactory;

/**
 * @author bakksjo
 */
public class NodeAdminImplTest {

    private final NodeAgentWithSchedulerFactory nodeAgentWithSchedulerFactory = mock(NodeAgentWithSchedulerFactory.class);
    private final NodeAgentContextFactory nodeAgentContextFactory = mock(NodeAgentContextFactory.class);
    private final ManualClock clock = new ManualClock();

    private final NodeAdminImpl nodeAdmin = new NodeAdminImpl(nodeAgentWithSchedulerFactory,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation), clock, Duration.ZERO, Duration.ZERO);

    @Test
    public void nodeAgentsAreProperlyLifeCycleManaged() {
        final NodeAgentContext context1 = createNodeAgentContext("host1.test.yahoo.com");
        final NodeAgentContext context2 = createNodeAgentContext("host2.test.yahoo.com");
        final NodeAgentWithScheduler nodeAgent1 = mockNodeAgentWithSchedulerFactory(context1);
        final NodeAgentWithScheduler nodeAgent2 = mockNodeAgentWithSchedulerFactory(context2);

        final InOrder inOrder = inOrder(nodeAgentWithSchedulerFactory, nodeAgent1, nodeAgent2);
        nodeAdmin.refreshContainersToRun(Set.of());
        verifyNoMoreInteractions(nodeAgentWithSchedulerFactory);

        nodeAdmin.refreshContainersToRun(Set.of(context1));
        inOrder.verify(nodeAgent1).start();
        inOrder.verify(nodeAgent2, never()).start();
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.refreshContainersToRun(Set.of(context1));
        inOrder.verify(nodeAgentWithSchedulerFactory, never()).create(any());
        inOrder.verify(nodeAgent1, never()).start();
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.refreshContainersToRun(Set.of());
        inOrder.verify(nodeAgentWithSchedulerFactory, never()).create(any());
        verify(nodeAgent1).stop();

        nodeAdmin.refreshContainersToRun(Set.of(context2));
        inOrder.verify(nodeAgent2).start();
        inOrder.verify(nodeAgent2, never()).stop();
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.refreshContainersToRun(Set.of());
        inOrder.verify(nodeAgentWithSchedulerFactory, never()).create(any());
        inOrder.verify(nodeAgent2, never()).start();
        inOrder.verify(nodeAgent2).stop();
        inOrder.verify(nodeAgent1, never()).start();
        inOrder.verify(nodeAgent1, never()).stop();
    }

    @Test
    public void testSetFrozen() {
        Set<NodeAgentContext> contexts = new HashSet<>();
        List<NodeAgentWithScheduler> nodeAgents = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            NodeAgentContext context = createNodeAgentContext("host" + i + ".test.yahoo.com");
            NodeAgentWithScheduler nodeAgent = mockNodeAgentWithSchedulerFactory(context);

            contexts.add(context);
            nodeAgents.add(nodeAgent);
        }

        nodeAdmin.refreshContainersToRun(contexts);

        assertTrue(nodeAdmin.isFrozen()); // Initially everything is frozen to force convergence
        mockNodeAgentSetFrozenResponse(nodeAgents, true, true, true);
        assertTrue(nodeAdmin.setFrozen(false)); // Unfreeze everything


        mockNodeAgentSetFrozenResponse(nodeAgents, false, false, false);
        assertFalse(nodeAdmin.setFrozen(true)); // NodeAdmin freezes only when all the NodeAgents are frozen

        mockNodeAgentSetFrozenResponse(nodeAgents, false, true, true);
        assertFalse(nodeAdmin.setFrozen(true));
        assertFalse(nodeAdmin.isFrozen());

        mockNodeAgentSetFrozenResponse(nodeAgents, true, true, true);
        assertTrue(nodeAdmin.setFrozen(true));
        assertTrue(nodeAdmin.isFrozen());

        mockNodeAgentSetFrozenResponse(nodeAgents, true, true, true);
        assertTrue(nodeAdmin.setFrozen(true));
        assertTrue(nodeAdmin.isFrozen());

        mockNodeAgentSetFrozenResponse(nodeAgents, false, false, false);
        assertFalse(nodeAdmin.setFrozen(false));
        assertFalse(nodeAdmin.isFrozen()); // NodeAdmin unfreezes instantly

        mockNodeAgentSetFrozenResponse(nodeAgents, false, false, true);
        assertFalse(nodeAdmin.setFrozen(false));
        assertFalse(nodeAdmin.isFrozen());

        mockNodeAgentSetFrozenResponse(nodeAgents, true, true, true);
        assertTrue(nodeAdmin.setFrozen(false));
        assertFalse(nodeAdmin.isFrozen());
    }

    @Test
    public void testSubsystemFreezeDuration() {
        // Initially everything is frozen to force convergence
        assertTrue(nodeAdmin.isFrozen());
        assertTrue(nodeAdmin.subsystemFreezeDuration().isZero());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(1), nodeAdmin.subsystemFreezeDuration());

        // Unfreezing floors freeze duration
        assertTrue(nodeAdmin.setFrozen(false)); // Unfreeze everything
        assertTrue(nodeAdmin.subsystemFreezeDuration().isZero());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(nodeAdmin.subsystemFreezeDuration().isZero());

        // Advancing time now will make freeze duration proceed according to clock
        assertTrue(nodeAdmin.setFrozen(true));
        assertTrue(nodeAdmin.subsystemFreezeDuration().isZero());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(1), nodeAdmin.subsystemFreezeDuration());
    }

    private void mockNodeAgentSetFrozenResponse(List<NodeAgentWithScheduler> nodeAgents, boolean... responses) {
        for (int i = 0; i < nodeAgents.size(); i++) {
            NodeAgentWithScheduler nodeAgent = nodeAgents.get(i);
            when(nodeAgent.setFrozen(anyBoolean(), any())).thenReturn(responses[i]);
        }
    }

    private NodeAgentContext createNodeAgentContext(String hostname) {
        NodeSpec nodeSpec = new NodeSpec.Builder()
                .hostname(hostname)
                .state(Node.State.active)
                .nodeType(NodeType.tenant)
                .flavor("default")
                .build();

        return new NodeAgentContextImpl.Builder(nodeSpec).build();
    }

    private NodeAgentWithScheduler mockNodeAgentWithSchedulerFactory(NodeAgentContext context) {
        NodeAgentWithScheduler nodeAgentWithScheduler = mock(NodeAgentWithScheduler.class);
        when(nodeAgentWithSchedulerFactory.create(eq(context))).thenReturn(nodeAgentWithScheduler);
        return nodeAgentWithScheduler;
    }
}
