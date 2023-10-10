// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.StorageNode;

/**
 * @author baldersheim
 */
public class ProtonProvider extends PersistenceEngine {

    public ProtonProvider(StorageNode parent) {
        super(parent, "provider");
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        StorServerConfig.Persistence_provider.Builder provider = new StorServerConfig.Persistence_provider.Builder();
        provider.type(StorServerConfig.Persistence_provider.Type.Enum.RPC);
        builder.persistence_provider(provider);
    }
}
