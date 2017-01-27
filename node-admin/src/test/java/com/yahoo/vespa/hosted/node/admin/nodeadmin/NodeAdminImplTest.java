// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
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
    private static final double MIN_CPU_CORES = 1.0;
    private static final double MIN_MAIN_MEMORY_AVAILABLE_GB = 1.0;
    private static final double MIN_DISK_AVAILABLE_GB = 1.0;

    // Trick to allow mocking of typed interface without casts/warnings.
    private interface NodeAgentFactory extends Function<String, NodeAgent> {}

    @Test
    public void nodeAgentsAreProperlyLifeCycleManaged() throws Exception {
        final DockerOperations dockerOperations = mock(DockerOperations.class);
        final Function<String, NodeAgent> nodeAgentFactory = mock(NodeAgentFactory.class);

        final NodeAdminImpl nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, Optional.empty(), 100,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation), Optional.empty());

        final NodeAgent nodeAgent1 = mock(NodeAgentImpl.class);
        final NodeAgent nodeAgent2 = mock(NodeAgentImpl.class);
        when(nodeAgentFactory.apply(any(String.class))).thenReturn(nodeAgent1).thenReturn(nodeAgent2);

        final String hostName = "host";
        final DockerImage dockerImage = new DockerImage("image");
        final ContainerName containerName = new ContainerName("container");
        final Container existingContainer = new Container(hostName, dockerImage, containerName, 5);
        final ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostName)
                .wantedDockerImage(dockerImage)
                .containerName(containerName)
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .minCpuCores(MIN_CPU_CORES)
                .minMainMemoryAvailableGb(MIN_MAIN_MEMORY_AVAILABLE_GB)
                .minDiskAvailableGb(MIN_DISK_AVAILABLE_GB)
                .build();

        final InOrder inOrder = inOrder(nodeAgentFactory, nodeAgent1, nodeAgent2);
        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), asList(existingContainer));
        verifyNoMoreInteractions(nodeAgentFactory);

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(asList(nodeSpec), asList(existingContainer));
        inOrder.verify(nodeAgentFactory).apply(hostName);
        inOrder.verify(nodeAgent1).start(100);
//        inOrder.verify(nodeAgent1).execute(NodeAgent.Command.RUN_ITERATION_NOW);
        inOrder.verify(nodeAgent1, never()).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(asList(nodeSpec), asList(existingContainer));
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent1, never()).start(1);
     //   inOrder.verify(nodeAgent1).execute(NodeAgent.Command.RUN_ITERATION_NOW);
        inOrder.verify(nodeAgent1, never()).stop();
        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), asList(existingContainer));
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        verify(nodeAgent1).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(asList(nodeSpec), asList(existingContainer));
        inOrder.verify(nodeAgentFactory).apply(hostName);
        inOrder.verify(nodeAgent2).start(100);
        inOrder.verify(nodeAgent2, never()).stop();

        nodeAdmin.synchronizeNodeSpecsToNodeAgents(Collections.emptyList(), Collections.emptyList());
        inOrder.verify(nodeAgentFactory, never()).apply(any(String.class));
        inOrder.verify(nodeAgent2, never()).start(1);
    //    inOrder.verify(nodeAgent2).execute(NodeAgent.Command.RUN_ITERATION_NOW);
        inOrder.verify(nodeAgent2).stop();

        verifyNoMoreInteractions(nodeAgent1);
        verifyNoMoreInteractions(nodeAgent2);
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
}
