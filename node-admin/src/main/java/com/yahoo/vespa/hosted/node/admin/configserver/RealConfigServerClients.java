// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.RealNodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.state.State;
import com.yahoo.vespa.hosted.node.admin.configserver.state.StateImpl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author freva
 */
public class RealConfigServerClients implements ConfigServerClients {

    private final SslConnectionSocketFactoryUpdater updater;

    // ConfigServerApi that talks to all config servers
    private final ConfigServerApi configServerApi;

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final ConcurrentHashMap<HostName, State> states = new ConcurrentHashMap<>();
    private final ConfigServerInfo configServerInfo;

    /**
     * Create config server clients against a real (remote) config server.
     *
     * If a client certificate is required, one will be requested from the config server
     * and kept up to date. On failure, this constructor will throw an exception and
     * the caller may retry later.
     */
    public RealConfigServerClients(SiaIdentityProvider identityProvider, ConfigServerInfo info) {
        this.configServerInfo = info;
        updater = SslConnectionSocketFactoryUpdater.createAndRefreshKeyStoreIfNeeded(identityProvider, info.getAthenzIdentity().get());

        configServerApi = ConfigServerApiImpl.create(info, updater);

        nodeRepository = new RealNodeRepository(configServerApi);
        orchestrator = new OrchestratorImpl(configServerApi);
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
    public State state(HostName hostname) {
        return states.computeIfAbsent(hostname, this::createState);
    }

    @Override
    public void stop() {
        updater.unregisterConfigServerApi(configServerApi);
        configServerApi.close();
        updater.close();
    }

    private State createState(HostName hostname) {
        ConfigServerApi configServerApi = ConfigServerApiImpl.createFor(
                configServerInfo, updater, hostname);

        return new StateImpl(configServerApi);
    }
}
