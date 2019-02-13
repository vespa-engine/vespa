// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.provision.Flavor;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.application.validation.RestartConfigs;

/**
 * Common class for config producers for storage and distributor nodes.
 *
 * TODO: Author
 */
@RestartConfigs({StorCommunicationmanagerConfig.class, StorStatusConfig.class,
                 StorServerConfig.class, LoadTypeConfig.class, MetricsmanagerConfig.class})
public abstract class ContentNode extends AbstractService
        implements StorCommunicationmanagerConfig.Producer, StorStatusConfig.Producer, StorServerConfig.Producer {

    protected int distributionKey;
    String rootDirectory;

    public ContentNode(AbstractConfigProducer parent, String clusterName, String rootDirectory, int distributionKey) {
        super(parent, "" + distributionKey);
        this.distributionKey = distributionKey;
        initialize(distributionKey);

        this.rootDirectory = rootDirectory;

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

    public void initialize(int distributionKey) {
        this.distributionKey = distributionKey;
        portsMeta.on(0).tag("messaging");
        portsMeta.on(1).tag("rpc").tag("status");
        portsMeta.on(2).tag("http").tag("status").tag("state");
    }

    @Override
    public int getPortCount() { return 3; }


    @Override
    public void getConfig(StorCommunicationmanagerConfig.Builder builder) {
        builder.mbusport(getRelativePort(0));
        builder.rpcport(getRelativePort(1));
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
