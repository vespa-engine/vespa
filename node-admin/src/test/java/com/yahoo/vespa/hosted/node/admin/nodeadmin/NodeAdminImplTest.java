// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import org.junit.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 */
public class NodeAdminImplTest {
    // Trick to allow mocking of typed interface without casts/warnings.
    private interface NodeAgentFactory extends Function<String, NodeAgent> {}
    private final Function<String, NodeAgent> nodeAgentFactory = mock(NodeAgentFactory.class);
    private final Runnable aclMaintainer = mock(Runnable.class);
    private final ManualClock clock = new ManualClock();

    private final NodeAdminImpl nodeAdmin = new NodeAdminImpl(nodeAgentFactory, aclMaintainer,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation), clock);

    @Test
    public void nodeAgentsAreProperlyLifeCycleManaged() {
        final String hostName1 = "host1.test.yahoo.com";
        final String hostName2 = "host2.test.yahoo.com";
        final NodeAgent nodeAgent1 = mock(NodeAgentImpl.class);
        final NodeAgent nodeAgent2 = mock(NodeAgentImpl.class);
        when(nodeAgentFactory.apply(eq(hostName1))).thenReturn(nodeAgent1);
        when(nodeAgentFactory.apply(eq(hostName2))).thenReturn(nodeAgent2);


        final InOrder inOrder = inOrder(nodeAgentFactory, nodeAgent1, nodeAgent2);
        nodeAdmin.synchronizeNodesToNodeAgents(Collections.emptySet());
        verifyNoMoreInteractions(nodeAgentFactory);

        nodeAdmin.synchronizeNodesToNodeAgents(Collections.singleton(hostName1));
        inOrder.verify(nodeAgentFactory).apply(hostName1);
        inOrder.verify(nodeAgent1).start();
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.synchronizeNodesToNodeAgents(Collections.singleton(hostName1));
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent1, never()).start();
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.synchronizeNodesToNodeAgents(Collections.emptySet());
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        verify(nodeAgent1).stop();

        nodeAdmin.synchronizeNodesToNodeAgents(Collections.singleton(hostName2));
        inOrder.verify(nodeAgentFactory).apply(hostName2);
        inOrder.verify(nodeAgent2).start();
        inOrder.verify(nodeAgent2, never()).stop();
        verify(nodeAgent1).stop();

        nodeAdmin.synchronizeNodesToNodeAgents(Collections.emptySet());
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent2, never()).start();
        inOrder.verify(nodeAgent2).stop();

        verifyNoMoreInteractions(nodeAgent1);
        verifyNoMoreInteractions(nodeAgent2);
    }

    @Test
    public void testSetFrozen() {
        List<NodeAgent> nodeAgents = new ArrayList<>();
        Set<String> existingContainerHostnames = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            final String hostName = "host" + i + ".test.yahoo.com";
            NodeAgent nodeAgent = mock(NodeAgent.class);
            nodeAgents.add(nodeAgent);
            when(nodeAgentFactory.apply(eq(hostName))).thenReturn(nodeAgent);

            existingContainerHostnames.add(hostName);
        }

        nodeAdmin.synchronizeNodesToNodeAgents(existingContainerHostnames);

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

    private void mockNodeAgentSetFrozenResponse(List<NodeAgent> nodeAgents, boolean... responses) {
        for (int i = 0; i < nodeAgents.size(); i++) {
            NodeAgent nodeAgent = nodeAgents.get(i);
            when(nodeAgent.setFrozen(anyBoolean())).thenReturn(responses[i]);
        }
    }
}
