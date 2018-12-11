// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

/**
 * @author Ulf Lilleengen
 */
public class RemoteSessionFactory {

    private final GlobalComponentRegistry componentRegistry;
    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Path sessionsPath;
    private final ConfigDefinitionRepo defRepo;
    private final TenantName tenant;
    private final ConfigserverConfig configserverConfig;

    public RemoteSessionFactory(GlobalComponentRegistry componentRegistry, TenantName tenant) {
        this.componentRegistry = componentRegistry;
        this.curator = componentRegistry.getCurator();
        this.configCurator = componentRegistry.getConfigCurator();
        this.sessionsPath = TenantRepository.getSessionsPath(tenant);
        this.tenant = tenant;
        this.defRepo = componentRegistry.getConfigDefinitionRepo();
        this.configserverConfig = componentRegistry.getConfigserverConfig();
    }

    public RemoteSession createSession(long sessionId) {
        Path sessionPath = this.sessionsPath.append(String.valueOf(sessionId));
        SessionZooKeeperClient sessionZKClient = new SessionZooKeeperClient(curator,
                                                                            configCurator,
                                                                            sessionPath,
                                                                            defRepo,
                                                                            configserverConfig.serverId(),
                                                                            componentRegistry.getZone().nodeFlavors());
        return new RemoteSession(tenant, sessionId, componentRegistry, sessionZKClient);
    }

}
