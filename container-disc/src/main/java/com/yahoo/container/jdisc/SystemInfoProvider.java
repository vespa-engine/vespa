// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.cloud.Cluster;
import ai.vespa.cloud.Environment;
import ai.vespa.cloud.Node;
import ai.vespa.cloud.SystemInfo;
import ai.vespa.cloud.Zone;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.QrConfig;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Provides information about the system in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class SystemInfoProvider extends AbstractComponent implements Provider<SystemInfo> {

    private final SystemInfo instance;

    @Inject
    public SystemInfoProvider(ConfigserverConfig csConfig, QrConfig qrConfig, ClusterInfoConfig ciConfig) {
        this.instance = new SystemInfo(new Zone(Environment.valueOf(csConfig.environment()), csConfig.region()),
                                       new Cluster(ciConfig.nodeCount()),
                                       new Node(qrConfig.nodeIndex()));
    }

    @Override
    public SystemInfo get() { return instance; }

}
