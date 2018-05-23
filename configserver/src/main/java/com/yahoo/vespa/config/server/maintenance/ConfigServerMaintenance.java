// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

public class ConfigServerMaintenance extends AbstractComponent {

    private static final Duration intervalInCd = Duration.ofMinutes(5);

    private final TenantsMaintainer tenantsMaintainer;
    private final ZooKeeperDataMaintainer zooKeeperDataMaintainer;

    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ConfigServerMaintenance(ConfigserverConfig configserverConfig,
                                   ApplicationRepository applicationRepository,
                                   Curator curator) {
        boolean isCd = configserverConfig.system().equals(SystemName.cd.name());
        Duration defaultInterval = isCd ? intervalInCd : Duration.ofMinutes(configserverConfig.maintainerIntervalMinutes());
        Duration tenantsMaintainerInterval = isCd ? intervalInCd : Duration.ofMinutes(configserverConfig.tenantsMaintainerIntervalMinutes());

        tenantsMaintainer = new TenantsMaintainer(applicationRepository, curator, tenantsMaintainerInterval);
        zooKeeperDataMaintainer = new ZooKeeperDataMaintainer(applicationRepository, curator, defaultInterval);
    }

    @Override
    public void deconstruct() {
        tenantsMaintainer.deconstruct();
        zooKeeperDataMaintainer.deconstruct();
    }

}
