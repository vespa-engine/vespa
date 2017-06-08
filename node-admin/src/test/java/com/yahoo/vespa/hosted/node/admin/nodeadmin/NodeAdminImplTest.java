// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
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

    @Test
    public void nodeAgentsAreProperlyLifeCycleManaged() throws Exception {
        final DockerOperations dockerOperations = mock(DockerOperations.class);
        final Function<String, NodeAgent> nodeAgentFactory = mock(NodeAgentFactory.class);

        final NodeAdminImpl nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, Optional.empty(), 100,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation), Optional.empty());

        final String hostName1 = "host1.test.yahoo.com";
        final String hostName2 = "host2.test.yahoo.com";
        final ContainerName containerName1 = ContainerName.fromHostname(hostName1);
        final NodeAgent nodeAgent1 = mock(NodeAgentImpl.class);
        final NodeAgent nodeAgent2 = mock(NodeAgentImpl.class);
        when(nodeAgentFactory.apply(eq(hostName1))).thenReturn(nodeAgent1);
        when(nodeAgentFactory.apply(eq(hostName2))).thenReturn(nodeAgent2);


        final InOrder inOrder = inOrder(nodeAgentFactory, nodeAgent1, nodeAgent2);
        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), Collections.singletonList(containerName1));
        verifyNoMoreInteractions(nodeAgentFactory);

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.singletonList(hostName1), Collections.singletonList(containerName1));
        inOrder.verify(nodeAgentFactory).apply(hostName1);
        inOrder.verify(nodeAgent1).start(100);
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.singletonList(hostName1), Collections.singletonList(containerName1));
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent1, never()).start(anyInt());
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), Collections.singletonList(containerName1));
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        verify(nodeAgent1).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.singletonList(hostName2), Collections.singletonList(containerName1));
        inOrder.verify(nodeAgentFactory).apply(hostName2);
        inOrder.verify(nodeAgent2).start(100);
        inOrder.verify(nodeAgent2, never()).stop();
        verify(nodeAgent1).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), Collections.emptyList());
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent2, never()).start(anyInt());
        inOrder.verify(nodeAgent2).stop();

        verifyNoMoreInteractions(nodeAgent1);
        verifyNoMoreInteractions(nodeAgent2);
    }

    @Test
    public void testSetFrozen() throws Exception {
        final DockerOperations dockerOperations = mock(DockerOperations.class);
        final Function<String, NodeAgent> nodeAgentFactory = mock(NodeAgentFactory.class);

        final NodeAdminImpl nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, Optional.empty(), 100,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation), Optional.empty());

        List<NodeAgent> nodeAgents = new ArrayList<>();
        List<String> existingContainerHostnames = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String hostName = "host" + i + ".test.yahoo.com";
            NodeAgent nodeAgent = mock(NodeAgent.class);
            nodeAgents.add(nodeAgent);
            when(nodeAgentFactory.apply(eq(hostName))).thenReturn(nodeAgent);

            existingContainerHostnames.add(hostName);
        }

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(existingContainerHostnames,
                existingContainerHostnames.stream().map(ContainerName::fromHostname).collect(Collectors.toList()));

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
    public void fullOuterJoinTest() {
        final List<String> strings = asList("3", "4", "5", "6", "7", "8", "9", "10");
        final List<Integer> integers = asList(1, 2, 3, 5, 8, 13, 21);
        final Set<Pair<Optional<String>, Optional<Integer>>> expectedResult = new HashSet<>(asList(
                newPair(null, 1),
                newPair(null, 2),
                newPair("3", 3),
                newPair("4", null),
                newPair("5", 5),
                newPair("6", null),
                newPair("7", null),
                newPair("8", 8),
                newPair("9", null),
                newPair("10", null),
                newPair(null, 13),
                newPair(null, 21)));

        assertThat(
                NodeAdminImpl.fullOuterJoin(
                        strings.stream(), string -> string,
                        integers.stream(), String::valueOf)
                        .collect(Collectors.toSet()),
                is(expectedResult));
    }

    private static <T, U> Pair<Optional<T>, Optional<U>> newPair(T t, U u) {
        return new Pair<>(Optional.ofNullable(t), Optional.ofNullable(u));
    }

    private void mockNodeAgentSetFrozenResponse(List<NodeAgent> nodeAgents, boolean... responses) {
        for (int i = 0; i < nodeAgents.size(); i++) {
            NodeAgent nodeAgent = nodeAgents.get(i);
            when(nodeAgent.setFrozen(anyBoolean())).thenReturn(responses[i]);
        }
    }
}
