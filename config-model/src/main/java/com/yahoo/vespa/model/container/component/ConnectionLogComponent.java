// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.vespa.model.container.ContainerCluster;

public class ConnectionLogComponent extends SimpleComponent implements ConnectionLogConfig.Producer {

    private final String logDirectoryName;
    private final String clusterName;
    private final boolean isHostedVespa;

    public ConnectionLogComponent(ContainerCluster<?> cluster, Class<? extends ConnectionLog> cls, String logDirectoryName) {
        this(cls, logDirectoryName, cluster.getName(), cluster.isHostedVespa());
    }

    public ConnectionLogComponent(Class<? extends ConnectionLog> cls, String logDirectoryName, String clusterName, boolean isHostedVespa) {
        super(cls.getName());
        this.logDirectoryName = logDirectoryName;
        this.clusterName = clusterName;
        this.isHostedVespa = isHostedVespa;
    }

    @Override
    public void getConfig(ConnectionLogConfig.Builder builder) {
        builder.cluster(clusterName);
        builder.logDirectoryName(logDirectoryName);
        if (isHostedVespa) {
            builder.useClusterIdInFileName(false);
        }
        builder.queueSize(-1);
    }
}
