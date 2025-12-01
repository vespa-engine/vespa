// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.utils.ResourceUtils;

import java.util.Optional;

import static com.yahoo.vespa.model.utils.ResourceUtils.GB;
import static com.yahoo.vespa.model.utils.ResourceUtils.GiB;

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
        return maybeRealNodeResources().map(res -> {
            int cores = (int)res.vcpu();
            // This should match the calculation used when node flavor is not available:
            // storage/src/vespa/storage/common/bucket_stripe_utils.cpp
            if (cores <= 16) {
                return 1;
            } else if (cores <= 64) {
                return 2;
            } else {
                return 4;
            }
        }).orElse(0); // Nodes use OS-reported CPU count instead
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        super.getConfig(builder);
        provider.getConfig(builder);
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        builder.num_distributor_stripes(tuneNumDistributorStripes());
        maybeRealNodeResources().ifPresent(res -> {
            // As fun as it would be to have 3.14 CPUs, the content layer deals in cold, unfeeling integers.
            builder.hwinfo.cpu.cores((int)Math.ceil(res.vcpu()));
            // For now, don't do any scaling of the memory reported to the distributor
            builder.hwinfo.memory.size((long)(ResourceUtils.usableMemoryGb(res) * GiB)); // TODO Gb vs GiB?!
            // The distributor does not use the disk in practice, but since we're already
            // providing hardware info, we might as well pass it along.
            builder.hwinfo.disk.size((long)(res.diskGb() * GB));
        });
    }

    @Override
    public Optional<String> getStartupCommand() {
        return Optional.of("exec sbin/vespa-distributord -c $VESPA_CONFIG_ID");
    }

    protected Optional<NodeResources> maybeRealNodeResources() {
        if (getHostResource() == null || getHostResource().realResources().isUnspecified()) {
            return Optional.empty();
        }
        return Optional.of(getHostResource().realResources());
    }

}
