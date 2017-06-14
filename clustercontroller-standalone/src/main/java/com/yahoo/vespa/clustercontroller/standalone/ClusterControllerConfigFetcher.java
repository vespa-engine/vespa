// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Used to manually fetch config for the stand alone application.
 */
package com.yahoo.vespa.clustercontroller.standalone;

import com.yahoo.vespa.clustercontroller.apps.clustercontroller.ClusterControllerClusterConfigurer;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.cloud.config.ZookeepersConfig;

import java.util.logging.Logger;

public class ClusterControllerConfigFetcher {
    private static Logger log = Logger.getLogger(ClusterControllerConfigFetcher.class.getName());

    private final ConfigSubscriber configSubscriber = new ConfigSubscriber();
    private final ConfigHandle<FleetcontrollerConfig> fleetcontrollerConfigHandle;
    private final ConfigHandle<SlobroksConfig> slobrokConfigHandle;
    private final ConfigHandle<StorDistributionConfig> distributionConfigHandle;
    private final ConfigHandle<ZookeepersConfig> zookeeperConfigHandle;

    private FleetControllerOptions options;

    public ClusterControllerConfigFetcher() throws Exception {
        String configId = createConfigId();
        log.fine("Using fleetcontroller config id \"" + configId + '"');
        String slobrokConfigId = createSlobrokConfigId();
        log.fine("Using slobrok config id \"" + slobrokConfigId + '"');

        fleetcontrollerConfigHandle = configSubscriber.subscribe(FleetcontrollerConfig.class, configId);
        slobrokConfigHandle = configSubscriber.subscribe(SlobroksConfig.class, slobrokConfigId);
        distributionConfigHandle = configSubscriber.subscribe(StorDistributionConfig.class, configId);
        zookeeperConfigHandle = configSubscriber.subscribe(ZookeepersConfig.class, configId);

        if (!configReady()) {
            throw new IllegalStateException("Initial configuration failed.");
        }
        options = generateOptions();
    }

    public void close() {
        log.fine("Shutting down fleetcontroller config subscription");
        configSubscriber.close();
    }

    private String createConfigId() {
        return System.getProperty("config.id");
    }

    private String createSlobrokConfigId() {
        return System.getProperty("slobrok.config.id");
    }

    public FleetControllerOptions getOptions() {
        return options;
    }

    public FleetControllerOptions generateOptions() throws Exception {
        ClusterControllerClusterConfigurer configurer = new ClusterControllerClusterConfigurer(
                null,
                distributionConfigHandle.getConfig(),
                fleetcontrollerConfigHandle.getConfig(),
                slobrokConfigHandle.getConfig(),
                zookeeperConfigHandle.getConfig(),
                null);
        return configurer.getOptions();
    }

    /** Test to see if the config has been updated, and if so, update the config. */
    public boolean updated(long timeoutMillis) throws Exception {
        if (configUpdated(timeoutMillis)) {
            log.fine("Updated fleetcontroller config.");
            options = generateOptions();
            return true;
        } else {
            return false;
        }
    }

    public long getGeneration() {
        return configSubscriber.getGeneration();
    }

    boolean configReady() {
        return configSubscriber.nextConfig();
    }

    boolean configUpdated(long timeoutMillis) {
        return configSubscriber.nextConfig(timeoutMillis);
    }
}
