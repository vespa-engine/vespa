// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonProvider;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;

import java.util.Objects;
import java.util.Optional;

/**
 * Class to provide config related to a specific storage node.
 */
@RestartConfigs({StorFilestorConfig.class})
public class StorageNode extends ContentNode implements StorServerConfig.Producer, StorFilestorConfig.Producer {

    static final String rootFolder = Defaults.getDefaults().underVespaHome("var/db/vespa/search/");

    private final Double capacity;
    private final boolean retired;
    private final StorageCluster cluster;

    public StorageNode(ModelContext.Properties properties, StorageCluster cluster,
                       Double capacity, int distributionKey, boolean retired) {
        super(properties.featureFlags(), cluster, cluster.getClusterName(),
              rootFolder + cluster.getClusterName() + "/storage/" + distributionKey,
              distributionKey);
        this.retired = retired;
        this.capacity = capacity;
        this.cluster = cluster;
    }

    @Override
    public Optional<String> getStartupCommand() {
        return isProviderProton()
                ? Optional.empty()
                : Optional.of("exec sbin/vespa-storaged -c $VESPA_CONFIG_ID");
    }

    public double getCapacity() {
        return Objects.requireNonNullElse(capacity, 1.0);
    }

    /** Whether this node is configured as retired, which means all content should migrate off the node */
    public boolean isRetired() { return retired; }

    private boolean isProviderProton() {
        for (var producer : getChildren().values()) {
            if (producer instanceof ProtonProvider) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        super.getConfig(builder);

        builder.node_capacity(getCapacity());

        for (var producer : getChildren().values()) {
            ((PersistenceEngine)producer).getConfig(builder);
        }
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (getHostResource() != null && ! getHostResource().realResources().isUnspecified()) {
            builder.num_threads(Math.max(4, (int)getHostResource().realResources().vcpu()));
        }
        cluster.getConfig(builder);
    }

}
