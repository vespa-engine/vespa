// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.SessionPreparer;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

import java.time.Clock;
import java.util.Optional;

/**
 * Interface representing all global config server components used within the config server.
 *
 * @author Ulf Lilleengen
 */
public interface GlobalComponentRegistry {

    Curator getCurator();
    ConfigCurator getConfigCurator();
    Metrics getMetrics();
    SessionPreparer getSessionPreparer();
    ConfigserverConfig getConfigserverConfig();
    TenantListener getTenantListener();
    ReloadListener getReloadListener();
    SuperModelGenerationCounter getSuperModelGenerationCounter();
    ConfigDefinitionRepo getConfigDefinitionRepo();
    PermanentApplicationPackage getPermanentApplicationPackage();
    HostRegistries getHostRegistries();
    ModelFactoryRegistry getModelFactoryRegistry();
    Optional<Provisioner> getHostProvisioner();
    Zone getZone();
    Clock getClock();
    ConfigServerDB getConfigServerDB();

}
