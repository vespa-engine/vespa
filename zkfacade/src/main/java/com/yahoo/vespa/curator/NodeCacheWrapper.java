// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;

import java.io.IOException;

/**
 * A file cache backed by a curator node cache.
 *
 * @author bratseth
 */
class NodeCacheWrapper implements Curator.FileCache {

    private final NodeCache wrapped;

    public NodeCacheWrapper(CuratorFramework curatorFramework, String path, boolean dataIsCompressed) {
        wrapped = new NodeCache(curatorFramework, path, dataIsCompressed);
    }

    @Override
    public void start() {
        try {
            wrapped.start(true);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the Curator cache", e);
        }
    }

    @Override
    public void addListener(NodeCacheListener listener) {
        wrapped.getListenable().addListener(listener);

    }

    @Override
    public ChildData getCurrentData() {
        return wrapped.getCurrentData();
    }

    @Override
    public void close() {
        try {
            wrapped.close();
        } catch (IOException e) {
            throw new RuntimeException("Exception closing curator cache", e);
        }
    }

}
