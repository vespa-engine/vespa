package com.yahoo.vespa.hosted.node.admin.nodeadmin;
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.integrationTests.CallOrderVerifier;
import com.yahoo.vespa.hosted.node.admin.integrationTests.OrchestratorMock;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        doAnswer(
                invocation -> {
                    List<ContainerNodeSpec> containersToRunInArgument = (List<ContainerNodeSpec>) invocation.getArguments()[0];
                    containersToRunInArgument.forEach(accumulatedArgumentList::add);
                    if (accumulatedArgumentList.size() == 2) {
                        throw new RuleBaseException("This exception is expected, and should show up in the log.");
                    }
                    return null;
                }
        ).when(nodeAdmin).refreshContainersToRun(anyList());

        final List<ContainerNodeSpec> containersToRun = new ArrayList<>();
        containersToRun.add(createSample());

        when(nodeRepository.getContainersToRun()).thenReturn(containersToRun);
        CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
        NodeAdminStateUpdater refresher = new NodeAdminStateUpdater(
                nodeRepository, nodeAdmin, Long.MAX_VALUE, Long.MAX_VALUE, orchestratorMock, "basehostname");

        // Non-frozen
        refresher.fetchContainersToRunFromNodeRepository();
        refresher.fetchContainersToRunFromNodeRepository();

        when(nodeAdmin.isFrozen()).thenReturn(true);
        int numberOfElementsBeforeFreeze = accumulatedArgumentList.size();

        // Frozen
        refresher.fetchContainersToRunFromNodeRepository();
        refresher.fetchContainersToRunFromNodeRepository();
        refresher.fetchContainersToRunFromNodeRepository();

        assertThat(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED),
                is(Optional.of("Not all node agents are frozen.")));

        assertThat(numberOfElementsBeforeFreeze, is(2));
        assertThat(accumulatedArgumentList.size(), is(numberOfElementsBeforeFreeze));


        assertThat(accumulatedArgumentList.get(0), is(createSample()));
        when(nodeAdmin.isFrozen()).thenReturn(false);

        assertThat(refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED),
                is(Optional.empty()));
        refresher.deconstruct();
    }

    private ContainerNodeSpec createSample() {
        return new ContainerNodeSpec.Builder()
                .hostname("host1.test.yahoo.com")
                .nodeState(Node.State.active)
                .nodeType("tenant")
                .nodeFlavor("docker")
                .build();
    }
}
