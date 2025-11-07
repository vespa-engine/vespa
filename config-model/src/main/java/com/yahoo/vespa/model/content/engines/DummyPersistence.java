// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.StorageNode;

public class DummyPersistence extends PersistenceEngine {

    public DummyPersistence(StorageNode parent) {
        super(parent, "provider");
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.persistence_provider(new StorServerConfig.Persistence_provider.Builder().type(StorServerConfig.Persistence_provider.Type.Enum.DUMMY));
    }

    public static class Factory implements PersistenceFactory {

        @Override
        public PersistenceEngine create(StorageNode storageNode) {
            return new DummyPersistence(storageNode);
        }

    }
}
