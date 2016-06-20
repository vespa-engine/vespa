package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Scenario test for NodeAdminStateUpdater.
 * @author dybis
 */
public class ResumeTest {
    @Test
    public void test() throws InterruptedException {
        NodeRepoMock nodeRepositoryMock = new NodeRepoMock();
        NodeAdminMock nodeAdminMock = new NodeAdminMock();
        OrchestratorMock orchestratorMock = new OrchestratorMock();

        nodeRepositoryMock.containerNodeSpecs.add(new ContainerNodeSpec(
                new HostName("hostname"),
                Optional.of(new DockerImage("dockerimage")),
                new ContainerName("containe"),
                NodeState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

        NodeAdminStateUpdater updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdminMock, 1, 1, orchestratorMock);
        // Wait for node admin to be notified with node repo state
        while (nodeAdminMock.getListOfHosts().size() == 0) {
            Thread.sleep(1);
        }

        // Make node admin and orchestrator block suspend
        orchestratorMock.suspendReturnValue = Optional.of("orch reject suspend");
        nodeAdminMock.frozen.set(false);
        assertThat(updater.setResumeStateAndCheckIfResumed(false), is(Optional.of("Not all node agents in correct state yet.")));

        // Now, change data in node repo, should not propagate.
        nodeRepositoryMock.containerNodeSpecs.clear();

        // Set node admin not blocking for suspend
        nodeAdminMock.frozen.set(true);
        assertThat(updater.setResumeStateAndCheckIfResumed(false), is(Optional.of("orch reject suspend")));

        // Make orchestrator allow suspend
        orchestratorMock.suspendReturnValue = Optional.empty();
        assertThat(updater.setResumeStateAndCheckIfResumed(false), is(Optional.empty()));

        // Now suspended, new node repo state should have not propagated to node admin
        Thread.sleep(2);
        assertThat(nodeAdminMock.getListOfHosts().size(), is(1));

        // Now resume
        assertThat(updater.setResumeStateAndCheckIfResumed(true), is(Optional.empty()));

        // Now node repo state should propagate to node admin again
        while (nodeAdminMock.getListOfHosts().size() != 0) {
            Thread.sleep(1);
        }

        updater.deconstruct();
    }
}
