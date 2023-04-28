// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Container cluster for cluster-controller containers.
 *
 * @author gjoranv
 * @author bjorncs
 */
public class ClusterControllerContainerCluster extends ContainerCluster<ClusterControllerContainer> {

    private static final Set<Path> UNNECESSARY_BUNDLES = Collections.unmodifiableSet(PlatformBundles.VESPA_SECURITY_BUNDLES);

    private final ReindexingContext reindexingContext;

    public ClusterControllerContainerCluster(
            TreeConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState, false);
        addDefaultHandlersWithVip();
        this.reindexingContext = createReindexingContext(deployState);
        setJvmGCOptions(deployState.getProperties().jvmGCOptions(Optional.of(ClusterSpec.Type.admin)));
    }

    @Override
    protected Set<Path> unnecessaryPlatformBundles() { return UNNECESSARY_BUNDLES; }

    @Override protected boolean messageBusEnabled() { return false; }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);

        builder.jvm.heapsize(128);
    }

    public ReindexingContext reindexingContext() { return reindexingContext; }

    private static ReindexingContext createReindexingContext(DeployState deployState) {
        return new ReindexingContext(deployState.reindexing().orElse(Reindexing.DISABLED_INSTANCE));
    }

}
