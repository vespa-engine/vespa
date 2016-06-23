package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.integrationTests.OrchestratorMock;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic test of ActiveContainersRefresherTest
 * @author dybis
 */
public class NodeAdminStateUpdaterTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testExceptionIsCaughtAndDataIsPassedAndFreeze() throws Exception {
        NodeRepository nodeRepository = mock(NodeRepository.class);
        NodeAdmin nodeAdmin = mock(NodeAdmin.class);
        final List<ContainerNodeSpec> accumulatedArgumentList = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch = new CountDownLatch(5);
        doAnswer((Answer<Object>) invocation -> {
            List<ContainerNodeSpec> containersToRunInArgument = (List<ContainerNodeSpec>) invocation.getArguments()[0];
            containersToRunInArgument.forEach(element -> accumulatedArgumentList.add(element));
            latch.countDown();
            if (accumulatedArgumentList.size() == 2) {
                throw new RuleBaseException("This exception is expected, and should show up in the log.");
            }
            return null;
        }).when(nodeAdmin).refreshContainersToRun(anyList());

        final List<ContainerNodeSpec> containersToRun = new ArrayList<>();
        containersToRun.add(createSample());

        when(nodeRepository.getContainersToRun()).thenReturn(containersToRun);
        OrchestratorMock orchestratorMock = new OrchestratorMock();
        NodeAdminStateUpdater refresher = new NodeAdminStateUpdater(
                nodeRepository, nodeAdmin, 1, 1, orchestratorMock, "basehostname");
        latch.await();
        int numberOfElements = accumulatedArgumentList.size();
        assertThat(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED),
                is(Optional.of("Not all node agents are frozen.")));
        assertTrue(numberOfElements > 4);
        assertThat(accumulatedArgumentList.get(0), is(createSample()));
        Thread.sleep(2);
        assertThat(accumulatedArgumentList.size(), is(numberOfElements));
        assertThat(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED),
                is(Optional.empty()));
        while (accumulatedArgumentList.size() == numberOfElements) {
            Thread.sleep(1);
        }
        refresher.deconstruct();
    }

    private ContainerNodeSpec createSample() {
        return new ContainerNodeSpec(
                new HostName("hostname"),
                Optional.empty(),
                new ContainerName("containername"),
                NodeState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
