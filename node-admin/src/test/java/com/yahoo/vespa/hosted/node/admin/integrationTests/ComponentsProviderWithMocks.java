// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.util.Collections;
import java.util.function.Function;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    static final Maintainer maintainer = new Maintainer();
    static final CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
    static final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
    static final StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
    static final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
    static final Docker dockerMock = new DockerMock(callOrderVerifier);

    private Environment environment = new Environment(Collections.emptySet(),
                                                      "dev",
                                                      "us-east-1",
                                                      "parent.host.name.yahoo.com",
                                                      new InetAddressResolver());
    private final MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
    private final DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment, maintainer, mr);
    private final Function<String, NodeAgent> nodeAgentFactory =
            (hostName) -> new NodeAgentImpl(hostName,
                                            nodeRepositoryMock, orchestratorMock, dockerOperations,
                                            maintenanceSchedulerMock, mr,
                                            environment, maintainer);
    private NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);


    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }

    @Override
    public MetricReceiverWrapper getMetricReceiverWrapper() {
        return null;
    }
}
