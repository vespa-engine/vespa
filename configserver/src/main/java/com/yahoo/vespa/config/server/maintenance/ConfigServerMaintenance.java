// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

public class ConfigServerMaintenance extends AbstractComponent {

    private final TenantsMaintainer tenantsMaintainer;
    private final ZooKeeperDataMaintainer zooKeeperDataMaintainer;

    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ConfigServerMaintenance(ConfigserverConfig configserverConfig,
                                   ApplicationRepository applicationRepository,
                                   Curator curator) {
        DefaultTimes defaults = new DefaultTimes(configserverConfig);
        tenantsMaintainer = new TenantsMaintainer(applicationRepository, curator, defaults.tenantsMaintainerInterval);
        zooKeeperDataMaintainer = new ZooKeeperDataMaintainer(applicationRepository, curator, defaults.zookeeperDataMaintainerInterval);
    }

    @Override
    public void deconstruct() {
        tenantsMaintainer.deconstruct();
        zooKeeperDataMaintainer.deconstruct();
    }

    /*
     * Default values from config. If one of the values needs to be changed, add the value to
     * configserver-config.xml in the config server application directory and restart the config server
     */
    private static class DefaultTimes {

        private final Duration defaultInterval;
        private final Duration tenantsMaintainerInterval;
        private final Duration zookeeperDataMaintainerInterval;

        DefaultTimes(ConfigserverConfig configserverConfig) {
            boolean isCd = configserverConfig.system().equals(SystemName.cd.name());

            this.defaultInterval = Duration.ofMinutes(configserverConfig.maintainerIntervalMinutes());
            // TODO: Want job control or feature flag to control when to run this, for now use a very long interval unless in CD
            this.tenantsMaintainerInterval = isCd ? defaultInterval : Duration.ofMinutes(configserverConfig.tenantsMaintainerIntervalMinutes());
            this.zookeeperDataMaintainerInterval = defaultInterval;
        }
    }

}
