// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.session.SessionPreparer;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Interface representing all global config server components used within the config server.
 *
 * @author Ulf Lilleengen
 */
public interface GlobalComponentRegistry {

    SessionPreparer getSessionPreparer();
    ConfigserverConfig getConfigserverConfig();
    TenantListener getTenantListener();
    ReloadListener getReloadListener();
    ConfigDefinitionRepo getStaticConfigDefinitionRepo();
    PermanentApplicationPackage getPermanentApplicationPackage();
    ModelFactoryRegistry getModelFactoryRegistry();
    Optional<Provisioner> getHostProvisioner();
    Zone getZone();
    Clock getClock();
    ConfigServerDB getConfigServerDB();
    FlagSource getFlagSource();
    ExecutorService getZkCacheExecutor();
    SecretStore getSecretStore();
}
