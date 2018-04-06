// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.RealNodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorImpl;

import java.util.Optional;

/**
 * @author freva
 */
public class RealConfigServerClients implements ConfigServerClients {

    private final Optional<ConfigServerApi> configServerApi;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;

    public RealConfigServerClients(Environment environment) {
        this(new SslConfigServerApiImpl(environment));
    }

    public RealConfigServerClients(ConfigServerInfo info, String hostname) {
        this(new SslConfigServerApiImpl(info, hostname));
    }

    public RealConfigServerClients(NodeRepository nodeRepository, Orchestrator orchestrator) {
        this(nodeRepository, orchestrator, Optional.empty());
    }

    private RealConfigServerClients(ConfigServerApi configServerApi) {
        this(new RealNodeRepository(configServerApi), new OrchestratorImpl(configServerApi), Optional.of(configServerApi));
    }

    private RealConfigServerClients(NodeRepository nodeRepository, Orchestrator orchestrator,
                                    Optional<ConfigServerApi> configServerApi) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.configServerApi = configServerApi;
    }

    @Override
    public NodeRepository nodeRepository() {
        return nodeRepository;
    }

    @Override
    public Orchestrator orchestrator() {
        return orchestrator;
    }

    @Override
    public void stop() {
        configServerApi.ifPresent(ConfigServerApi::close);
    }
}
