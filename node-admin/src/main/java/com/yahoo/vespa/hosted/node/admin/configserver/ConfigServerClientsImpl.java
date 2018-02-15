// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorImpl;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.Optional;

/**
 * @author freva
 */
public class ConfigServerClientsImpl implements ConfigServerClients {

    private final Optional<ConfigServerApi> configServerApi;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;

    public ConfigServerClientsImpl(Environment environment) {
        this(new SslConfigServerApiImpl(environment));
    }

    public ConfigServerClientsImpl(NodeRepository nodeRepository, Orchestrator orchestrator) {
        this(nodeRepository, orchestrator, Optional.empty());
    }

    private ConfigServerClientsImpl(ConfigServerApi configServerApi) {
        this(new NodeRepositoryImpl(configServerApi), new OrchestratorImpl(configServerApi), Optional.of(configServerApi));
    }

    private ConfigServerClientsImpl(NodeRepository nodeRepository, Orchestrator orchestrator,
                                    Optional<ConfigServerApi> configServerApi) {
        Security.addProvider(new BouncyCastleProvider());

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
        configServerApi.ifPresent(ConfigServerApi::stop);
    }
}
