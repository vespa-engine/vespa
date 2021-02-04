// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.util.OptionalInt;

public class ConnectionLogComponent extends SimpleComponent implements ConnectionLogConfig.Producer {

    private final String logDirectoryName;
    private final String clusterName;
    private final int queueSize;

    public ConnectionLogComponent(ContainerCluster<?> cluster, Class<? extends ConnectionLog> cls, String logDirectoryName) {
        this(cluster, cls, logDirectoryName, cluster.getName());
    }

    public ConnectionLogComponent(ContainerCluster<?> cluster, Class<? extends ConnectionLog> cls, String logDirectoryName, String clusterName) {
        super(new ComponentModel(cls.getName(), null, "jdisc_http_service", null));
        this.logDirectoryName = logDirectoryName;
        this.clusterName = clusterName;
        this.queueSize = queueSize(cluster).orElse(-1);
    }

    private static OptionalInt queueSize(ContainerCluster<?> cluster) {
        if (cluster == null) return OptionalInt.empty();
        double vcpu = cluster.vcpu().orElse(0);
        if (vcpu <= 0) return OptionalInt.empty();
        return OptionalInt.of((int) Math.max(4096, Math.ceil(vcpu * 512.0)));
    }

    @Override
    public void getConfig(ConnectionLogConfig.Builder builder) {
        builder.cluster(clusterName);
        builder.logDirectoryName(logDirectoryName);
        if (queueSize >= 0) {
            builder.queueSize(queueSize);
        }
    }
}
