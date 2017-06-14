// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.search.SearchNode;

/**
 * @author baldersheim
 */
public class ProtonProvider extends RPCEngine {
    public ProtonProvider(StorageNode parent, SearchNode searchNode) {
        super(parent, searchNode);
    }
}
