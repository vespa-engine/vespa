// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.search.SearchNode;

/**
 * Created with IntelliJ IDEA.
 * User: balder
 * Date: 20.11.12
 * Time: 20:04
 * To change this template use File | Settings | File Templates.
 */
public class ProtonProvider extends RPCEngine {
    public ProtonProvider(StorageNode parent, SearchNode searchNode) {
        super(parent, searchNode);
    }
}
