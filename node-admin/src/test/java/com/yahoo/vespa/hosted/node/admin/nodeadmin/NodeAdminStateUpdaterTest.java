// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.OrchestratorStatus;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.integration.NodeRepoMock;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Basic test of NodeAdminStateUpdater
 *
 * @author freva
 */
public class NodeAdminStateUpdaterTest {
    private final NodeAgentContextFactory nodeAgentContextFactory = mock(NodeAgentContextFactory.class);
    private final NodeRepoMock nodeRepository = spy(new NodeRepoMock());
    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private final NodeAdmin nodeAdmin = mock(NodeAdmin.class);
    private final HostName hostHostname = HostName.of("basehost1.test.yahoo.com");

    private final NodeAdminStateUpdater updater = spy(new NodeAdminStateUpdater(
            nodeAgentContextFactory, nodeRepository, orchestrator, nodeAdmin, hostHostname));


    @Test
    void state_convergence() {
        mockNodeRepo(NodeState.active, 4);
        List<String> activeHostnames = nodeRepository.getNodes(hostHostname.value()).stream()
                .map(NodeSpec::hostname)
                .toList();
        List<String> suspendHostnames = new ArrayList<>(activeHostnames);
        suspendHostnames.add(hostHostname.value());
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofSeconds(1));

        {
            // Initially everything is frozen to force convergence
            assertConvergeError(RESUMED, "NodeAdmin is not yet unfrozen");
            when(nodeAdmin.setFrozen(eq(false))).thenReturn(true);
            updater.converge(RESUMED);
            verify(orchestrator, times(1)).resume(hostHostname.value());

            // We are already resumed, so this should return without resuming again
            updater.converge(RESUMED);
            verify(orchestrator, times(1)).resume(hostHostname.value());
            verify(nodeAdmin, times(2)).setFrozen(eq(false));

            // Host is externally suspended in orchestrator, should be resumed by node-admin
            setHostOrchestratorStatus(hostHostname, OrchestratorStatus.ALLOWED_TO_BE_DOWN);
            updater.converge(RESUMED);
            verify(orchestrator, times(2)).resume(hostHostname.value());
            verify(nodeAdmin, times(3)).setFrozen(eq(false));
            setHostOrchestratorStatus(hostHostname, OrchestratorStatus.NO_REMARKS);

            // Lets try to suspend node admin only
            when(nodeAdmin.setFrozen(eq(true))).thenReturn(false);
            assertConvergeError(SUSPENDED_NODE_ADMIN, "NodeAdmin is not yet frozen");
            verify(nodeAdmin, times(3)).setFrozen(eq(false));
        }

        {
            // First orchestration failure happens within the freeze convergence timeout,
            // and so should not call setFrozen(false)
            final String exceptionMessage = "Cannot allow to suspend because some reason";
            when(nodeAdmin.setFrozen(eq(true))).thenReturn(true);
            doThrow(new RuntimeException(exceptionMessage)).doNothing()
                    .when(orchestrator).suspend(eq(hostHostname.value()));
            assertConvergeError(SUSPENDED_NODE_ADMIN, exceptionMessage);
            verify(nodeAdmin, times(3)).setFrozen(eq(false));

            updater.converge(SUSPENDED_NODE_ADMIN);
            verify(nodeAdmin, times(3)).setFrozen(eq(false));
            verify(orchestrator, times(2)).suspend(hostHostname.value());
            setHostOrchestratorStatus(hostHostname, OrchestratorStatus.ALLOWED_TO_BE_DOWN);

            // Already suspended, no changes
            updater.converge(SUSPENDED_NODE_ADMIN);
            verify(nodeAdmin, times(3)).setFrozen(eq(false));
            verify(orchestrator, times(2)).suspend(hostHostname.value());

            // Host is externally resumed
            setHostOrchestratorStatus(hostHostname, OrchestratorStatus.NO_REMARKS);
            updater.converge(SUSPENDED_NODE_ADMIN);
            verify(nodeAdmin, times(3)).setFrozen(eq(false));
            verify(orchestrator, times(3)).suspend(hostHostname.value());
            setHostOrchestratorStatus(hostHostname, OrchestratorStatus.ALLOWED_TO_BE_DOWN);
        }

        {
            // At this point orchestrator will say its OK to suspend, but something goes wrong when we try to stop services
            final String exceptionMessage = "Failed to stop services";
            verify(orchestrator, times(0)).suspend(eq(hostHostname.value()), eq(suspendHostnames));
            doThrow(new RuntimeException(exceptionMessage)).doNothing().when(nodeAdmin).stopNodeAgentServices();
            assertConvergeError(SUSPENDED, exceptionMessage);
            verify(orchestrator, times(1)).suspend(eq(hostHostname.value()), eq(suspendHostnames));
            // Make sure we dont roll back if we fail to stop services - we will try to stop again next tick
            verify(nodeAdmin, times(3)).setFrozen(eq(false));

            // Finally we are successful in transitioning to frozen
            updater.converge(SUSPENDED);
        }
    }

    @Test
    void half_transition_revert() {
        final String exceptionMsg = "Cannot allow to suspend because some reason";
        mockNodeRepo(NodeState.active, 3);

        // Initially everything is frozen to force convergence
        when(nodeAdmin.setFrozen(eq(false))).thenReturn(true);
        updater.converge(RESUMED);
        verify(nodeAdmin, times(1)).setFrozen(eq(false));
        verify(nodeAdmin, times(1)).refreshContainersToRun(any());

        // Let's start suspending, we are able to freeze the nodes, but orchestrator denies suspension
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofSeconds(1));
        when(nodeAdmin.setFrozen(eq(true))).thenReturn(true);
        doThrow(new RuntimeException(exceptionMsg)).when(orchestrator).suspend(eq(hostHostname.value()));

        assertConvergeError(SUSPENDED_NODE_ADMIN, exceptionMsg);
        verify(nodeAdmin, times(1)).setFrozen(eq(true));
        verify(orchestrator, times(1)).suspend(eq(hostHostname.value()));
        assertConvergeError(SUSPENDED_NODE_ADMIN, exceptionMsg);
        verify(nodeAdmin, times(2)).setFrozen(eq(true));
        verify(orchestrator, times(2)).suspend(eq(hostHostname.value()));
        assertConvergeError(SUSPENDED_NODE_ADMIN, exceptionMsg);
        verify(nodeAdmin, times(3)).setFrozen(eq(true));
        verify(orchestrator, times(3)).suspend(eq(hostHostname.value()));

        // No new unfreezes nor refresh while trying to freeze
        verify(nodeAdmin, times(1)).setFrozen(eq(false));
        verify(nodeAdmin, times(1)).refreshContainersToRun(any());

        // Only resume and fetch containers when subsystem freeze duration expires
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofHours(1));
        assertConvergeError(SUSPENDED_NODE_ADMIN, "Timed out trying to freeze all nodes: will force an unfrozen tick");
        verify(nodeAdmin, times(2)).setFrozen(eq(false));
        verify(orchestrator, times(3)).suspend(eq(hostHostname.value())); // no new suspend calls
        verify(nodeAdmin, times(2)).refreshContainersToRun(any());

        // We change our mind, want to remain resumed
        updater.converge(RESUMED);
        verify(nodeAdmin, times(3)).setFrozen(eq(false)); // Make sure that we unfreeze!
    }

    @Test
    void do_not_orchestrate_host_when_not_active() {
        when(nodeAdmin.subsystemFreezeDuration()).thenReturn(Duration.ofHours(1));
        when(nodeAdmin.setFrozen(anyBoolean())).thenReturn(true);
        mockNodeRepo(NodeState.ready, 3);

        // Resume and suspend only require that node-agents are frozen and permission from
        // orchestrator to resume/suspend host. Therefore, if host is not active, we only need to freeze.
        updater.converge(RESUMED);
        verify(orchestrator, never()).resume(eq(hostHostname.value()));

        updater.converge(SUSPENDED_NODE_ADMIN);
        verify(orchestrator, never()).suspend(eq(hostHostname.value()));

        // When doing batch suspend, only suspend the containers if the host is not active
        List<String> activeHostnames = nodeRepository.getNodes(hostHostname.value()).stream()
                .map(NodeSpec::hostname)
                .collect(Collectors.toList());
        updater.converge(SUSPENDED);
        verify(orchestrator, times(1)).suspend(eq(hostHostname.value()), eq(activeHostnames));
    }

    @Test
    void node_spec_and_acl_aligned() {
        Acl acl = new Acl.Builder().withTrustedPorts(22).build();
        mockNodeRepo(NodeState.active, 3);
        mockAcl(acl, 1, 2, 3);

        updater.adjustNodeAgentsToRunFromNodeRepository();
        updater.adjustNodeAgentsToRunFromNodeRepository();
        updater.adjustNodeAgentsToRunFromNodeRepository();

        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host1.yahoo.com")), eq(acl));
        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host2.yahoo.com")), eq(acl));
        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host3.yahoo.com")), eq(acl));
        verify(nodeRepository, times(3)).getNodes(eq(hostHostname.value()));
        verify(nodeRepository, times(3)).getAcls(eq(hostHostname.value()));
    }

    @Test
    void node_spec_and_acl_mismatch_missing_one_acl() {
        Acl acl = new Acl.Builder().withTrustedPorts(22).build();
        mockNodeRepo(NodeState.active, 3);
        mockAcl(acl, 1, 2); // Acl for 3 is missing

        updater.adjustNodeAgentsToRunFromNodeRepository();
        mockNodeRepo(NodeState.active, 2); // Next tick, the spec for 3 is no longer returned
        updater.adjustNodeAgentsToRunFromNodeRepository();
        updater.adjustNodeAgentsToRunFromNodeRepository();

        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host1.yahoo.com")), eq(acl));
        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host2.yahoo.com")), eq(acl));
        verify(nodeAgentContextFactory, times(1)).create(argThat(spec -> spec.hostname().equals("host3.yahoo.com")), eq(Acl.EMPTY));
        verify(nodeRepository, times(3)).getNodes(eq(hostHostname.value()));
        verify(nodeRepository, times(3)).getAcls(eq(hostHostname.value()));
    }

    @Test
    void node_spec_and_acl_mismatch_additional_acl() {
        Acl acl = new Acl.Builder().withTrustedPorts(22).build();
        mockNodeRepo(NodeState.active, 2);
        mockAcl(acl, 1, 2, 3); // Acl for 3 is extra

        updater.adjustNodeAgentsToRunFromNodeRepository();
        updater.adjustNodeAgentsToRunFromNodeRepository();
        updater.adjustNodeAgentsToRunFromNodeRepository();

        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host1.yahoo.com")), eq(acl));
        verify(nodeAgentContextFactory, times(3)).create(argThat(spec -> spec.hostname().equals("host2.yahoo.com")), eq(acl));
        verify(nodeRepository, times(3)).getNodes(eq(hostHostname.value()));
        verify(nodeRepository, times(3)).getAcls(eq(hostHostname.value()));
    }

    private void assertConvergeError(NodeAdminStateUpdater.State targetState, String reason) {
        try {
            updater.converge(targetState);
            fail("Expected converging to " + targetState + " to fail with \"" + reason + "\", but it succeeded without error");
        } catch (RuntimeException e) {
            assertEquals(reason, e.getMessage());
        }
    }

    private void mockNodeRepo(NodeState hostState, int numberOfNodes) {
        nodeRepository.resetNodeSpecs();

        IntStream.rangeClosed(1, numberOfNodes)
                .mapToObj(i -> NodeSpec.Builder.testSpec("host" + i + ".yahoo.com").parentHostname(hostHostname.value()).build())
                .forEach(nodeRepository::updateNodeSpec);

        nodeRepository.updateNodeSpec(NodeSpec.Builder.testSpec(hostHostname.value(), hostState).type(NodeType.host).build());
    }

    private void mockAcl(Acl acl, int... nodeIds) {
        nodeRepository.setAcl(Arrays.stream(nodeIds)
                .mapToObj(i -> "host" + i + ".yahoo.com")
                .collect(Collectors.toMap(Function.identity(), h -> acl)));
    }

    private void setHostOrchestratorStatus(HostName hostname, OrchestratorStatus orchestratorStatus) {
        nodeRepository.updateNodeSpec(hostname.value(), node -> node.orchestratorStatus(orchestratorStatus));
    }
}
