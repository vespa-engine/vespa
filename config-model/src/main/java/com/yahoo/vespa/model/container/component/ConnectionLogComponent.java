// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.vespa.model.container.ContainerCluster;

public class ConnectionLogComponent extends SimpleComponent implements ConnectionLogConfig.Producer {

    private final String logDirectoryName;
    private final String clusterName;

    public ConnectionLogComponent(ContainerCluster<?> cluster, Class<? extends ConnectionLog> cls, String logDirectoryName) {
        this(cls, logDirectoryName, cluster.getName());
    }

    public ConnectionLogComponent(Class<? extends ConnectionLog> cls, String logDirectoryName, String clusterName) {
        super(cls.getName());
        this.logDirectoryName = logDirectoryName;
        this.clusterName = clusterName;
    }

    @Override
    public void getConfig(ConnectionLogConfig.Builder builder) {
        builder.cluster(clusterName);
        builder.logDirectoryName(logDirectoryName);
        builder.queueSize(-1);
    }
}
