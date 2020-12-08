// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * Container cluster for cluster-controller containers.
 *
 * @author gjoranv
 * @author bjorncs
 */
public class ClusterControllerContainerCluster extends ContainerCluster<ClusterControllerContainer>
{

    private final ReindexingContext reindexingContext;

    public ClusterControllerContainerCluster(
            AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState, false);
        addDefaultHandlersWithVip();
        this.reindexingContext = createReindexingContext(deployState);
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

    @Override protected boolean messageBusEnabled() { return false; }

    public ReindexingContext reindexingContext() { return reindexingContext; }

    private static ReindexingContext createReindexingContext(DeployState deployState) {
        Reindexing reindexing = deployState.featureFlags().enableAutomaticReindexing()
                ? deployState.reindexing().orElse(Reindexing.DISABLED_INSTANCE)
                : Reindexing.DISABLED_INSTANCE;
        return new ReindexingContext(reindexing);
    }

}
