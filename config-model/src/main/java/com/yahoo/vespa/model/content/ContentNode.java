// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.application.validation.RestartConfigs;

/**
 * Common class for config producers for storage and distributor nodes.
 */
@RestartConfigs({StorCommunicationmanagerConfig.class, StorStatusConfig.class,
                 StorServerConfig.class, MetricsmanagerConfig.class})
public abstract class ContentNode extends AbstractService
        implements StorCommunicationmanagerConfig.Producer, StorStatusConfig.Producer, StorServerConfig.Producer {

    private final int distributionKey;
    private final String rootDirectory;
    private final int mbus_network_threads;
    private final int mbus_rpc_targets;
    private final int mbus_events_before_wakeup;
    private final int rpc_num_targets;
    private final int rpc_events_before_wakeup;

    public ContentNode(ModelContext.FeatureFlags featureFlags, TreeConfigProducer<?> parent, String clusterName, String rootDirectory, int distributionKey) {
        super(parent, "" + distributionKey);
        this.distributionKey = distributionKey;
        this.rootDirectory = rootDirectory;
        mbus_network_threads = featureFlags.mbusNetworkThreads();
        mbus_rpc_targets = featureFlags.mbusCppRpcNumTargets();
        mbus_events_before_wakeup = featureFlags.mbusCppEventsBeforeWakeup();
        rpc_num_targets = featureFlags.rpcNumTargets();
        rpc_events_before_wakeup = featureFlags.rpcEventsBeforeWakeup();

        initialize();
        setProp("clustertype", "content");
        setProp("clustername", clusterName);
        setProp("index", distributionKey);
    }

    public int getDistributionKey() {
        return distributionKey;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.root_folder(rootDirectory);
        builder.node_index(distributionKey);
    }

    private void initialize() {
        portsMeta.on(0).tag("messaging");
        portsMeta.on(1).tag("rpc").tag("status");
        portsMeta.on(2).tag("http").tag("status").tag("state");
    }

    @Override
    public int getPortCount() { return 3; }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) {
            from.allocatePort("messaging");
            from.allocatePort("rpc");
            from.allocatePort("http");
        } else {
            from.wantPort(start++, "messaging");
            from.wantPort(start++, "rpc");
            from.wantPort(start++, "http");
        }
    }

    @Override
    public void getConfig(StorCommunicationmanagerConfig.Builder builder) {
        builder.mbusport(getRelativePort(0));
        builder.rpcport(getRelativePort(1));
        builder.mbus.num_network_threads(mbus_network_threads);
        builder.mbus.num_rpc_targets(mbus_rpc_targets);
        builder.mbus.events_before_wakeup(mbus_events_before_wakeup);
        builder.rpc.num_targets_per_node(rpc_num_targets);
        builder.rpc.events_before_wakeup(rpc_events_before_wakeup);
    }

    @Override
    public void getConfig(StorStatusConfig.Builder builder) {
        builder.httpport(getRelativePort(2));
    }

    @Override
    public int getHealthPort()  {
        return getRelativePort(2);
    }
}
