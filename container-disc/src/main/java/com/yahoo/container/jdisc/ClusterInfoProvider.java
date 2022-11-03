
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.cloud.Cluster;
import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Provides information about the zone in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class ClusterInfoProvider extends AbstractComponent implements Provider<Cluster> {

    private final Cluster instance;

    @Inject
    public ClusterInfoProvider(ClusterInfoConfig cfg) {
        this.instance = new Cluster(cfg.clusterId(), cfg.nodeCount(), cfg.nodeIndices());
    }

    @Override
    public Cluster get() { return instance; }

}
