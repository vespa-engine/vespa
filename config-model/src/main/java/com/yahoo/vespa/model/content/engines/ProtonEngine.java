// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.model.content.StorageNode;

/**
 * Initializes the engines engine on each storage node. May include creating other
 * nodes.
 */
public class ProtonEngine {
    public static class Factory implements PersistenceEngine.PersistenceFactory {

        @Override
        public PersistenceEngine create(StorageNode storageNode) {
            return new ProtonProvider(storageNode);
        }

    }
}
