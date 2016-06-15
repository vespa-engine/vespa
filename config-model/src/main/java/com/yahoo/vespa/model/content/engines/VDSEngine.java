// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.config.storage.StorMemfilepersistenceConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Configuration class to generate config for the memfile engines provider.
 */
public class VDSEngine extends PersistenceEngine
        implements StorMemfilepersistenceConfig.Producer
{
    ModelElement tuning;

    public VDSEngine(StorageNode parent, ModelElement vdsConfig) {
        super(parent, "provider");

        if (vdsConfig != null) {
            this.tuning = vdsConfig.getChild("tuning");
        }

        if (parent != null) {
            parent.useVdsEngine();
        }
    }

    @Override
    public void getConfig(StorMemfilepersistenceConfig.Builder builder) {
        if (tuning == null) {
            return;
        }

        ModelElement diskFullRatio = tuning.getChild("disk-full-ratio");
        if (diskFullRatio != null) {
            builder.disk_full_factor(diskFullRatio.asDouble());
        }

        ModelElement cacheSize = tuning.getChild("cache-size");
        if (cacheSize != null) {
            builder.cache_size(cacheSize.asLong());
        }
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.persistence_provider(
                new StorServerConfig.Persistence_provider.Builder().type(
                        StorServerConfig.Persistence_provider.Type.Enum.STORAGE)
        );
    }

    public static class Factory implements PersistenceFactory {
        ModelElement vdsConfig;

        public Factory(ModelElement vdsConfig) {
            this.vdsConfig = vdsConfig;
        }

        @Override
        public PersistenceEngine create(StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement) {
            return new VDSEngine(storageNode, vdsConfig);
        }

        @Override
        public boolean supportRevert() {
            return true;
        }

        @Override
        public boolean enableMultiLevelSplitting() {
            return true;
        }

        @Override
        public ContentCluster.DistributionMode getDefaultDistributionMode() {
            return ContentCluster.DistributionMode.STRICT;
        }
    }
}
