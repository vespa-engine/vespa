// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Container cluster for cluster-controller containers.
 *
 * @author gjoranv
 * @author bjorncs
 */
public class ClusterControllerContainerCluster extends ContainerCluster<ClusterControllerContainer> {

    private static final Set<Path> UNNECESSARY_BUNDLES = Set.copyOf(PlatformBundles.VESPA_SECURITY_BUNDLES);

    private final ReindexingContext reindexingContext;

    private int totalNumberOfContentNodes = 0;

    public ClusterControllerContainerCluster(
            TreeConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState, false);
        addDefaultHandlersWithVip();
        this.reindexingContext = createReindexingContext(deployState);
        setJvmGCOptions(deployState.getProperties().jvmGCOptions(Optional.of(ClusterSpec.Type.admin),
                                                                 Optional.of(ClusterSpec.Id.from(name))));
        if (isHostedVespa())
            addAccessLog("controller");
    }

    @Override
    protected Set<Path> unnecessaryPlatformBundles() { return UNNECESSARY_BUNDLES; }

    @Override protected boolean messageBusEnabled() { return false; }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);

        builder.jvm.heapsize(calculateHeapSize());
    }

    public void updateNodeCount(int nodes) {
        totalNumberOfContentNodes += nodes;
    }

    private int calculateHeapSize() {
        int baseValue = 128;
        if (!isHostedVespa) return baseValue;

        return Math.min(baseValue + calculateJvmHeapAdjustment(), 400);
    }

    private int calculateJvmHeapAdjustment() {
        // Heuristic to set JVM heap size for cluster controller
        // 300 nodes => need heap size of about 400 MiB, minimum heap should be 128 MiB
        // Increase in steps to avoid changes to heap size with small changes in node count.
        double adjustmentFactor = 0.75; // 0.75 MiB per node
        var step = totalNumberOfContentNodes / 50;
        return  (int) (step * adjustmentFactor * 50);
    }

    public ReindexingContext reindexingContext() { return reindexingContext; }

    private static ReindexingContext createReindexingContext(DeployState deployState) {
        return new ReindexingContext(deployState.reindexing().orElse(Reindexing.DISABLED_INSTANCE));
    }

}
