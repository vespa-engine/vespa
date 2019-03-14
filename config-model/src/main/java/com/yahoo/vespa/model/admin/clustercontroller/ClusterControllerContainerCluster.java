// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * Container cluster for cluster-controller containers.
 *
 * @author gjoranv
 */
public class ClusterControllerContainerCluster extends ContainerCluster<ClusterControllerContainer> implements
        ThreadpoolConfig.Producer
{
    public ClusterControllerContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState);
        addDefaultHandlersWithVip();
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

}
