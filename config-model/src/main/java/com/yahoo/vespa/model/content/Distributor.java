// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;

import java.util.Optional;

/**
 * Represents specific configuration for a given distributor node.
 */
public class Distributor extends ContentNode implements StorDistributormanagerConfig.Producer {

    PersistenceEngine provider;

    public Distributor(ModelContext.Properties properties, DistributorCluster parent, int distributionKey, PersistenceEngine provider) {
        super(properties.featureFlags(), parent, parent.getClusterName(),
             StorageNode.rootFolder + parent.getClusterName() + "/distributor/" + distributionKey, distributionKey);
        this.provider = provider;
        setMallocImpl(properties.mallocImpl(Optional.of(ClusterSpec.Type.content)));
    }

    private int tuneNumDistributorStripes() {
        if (getHostResource() != null &&
                !getHostResource().realResources().isUnspecified()) {
            int cores = (int)getHostResource().realResources().vcpu();
            // This should match the calculation used when node flavor is not available:
            // storage/src/vespa/storage/common/bucket_stripe_utils.cpp
            if (cores <= 16) {
                return 1;
            } else if (cores <= 64) {
                return 2;
            } else {
                return 4;
            }
        } else {
            return 0;
        }
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        super.getConfig(builder);
        provider.getConfig(builder);
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        builder.num_distributor_stripes(tuneNumDistributorStripes());
    }

    @Override
    public Optional<String> getStartupCommand() {
        return Optional.of("exec sbin/vespa-distributord -c $VESPA_CONFIG_ID");
    }

}
