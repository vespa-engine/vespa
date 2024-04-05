// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.path.Path;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * A directory cache backed by a curator path children cache.
 *
 * @author bratseth
 */
class PathChildrenCacheWrapper implements Curator.DirectoryCache {

    private final PathChildrenCache wrapped;

    public PathChildrenCacheWrapper(CuratorFramework curatorFramework, String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        wrapped = new PathChildrenCache(curatorFramework, path, cacheData, dataIsCompressed, executorService);
    }

    @Override
    public void start() {
        try {
            wrapped.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the Curator cache", e);
        }
    }

    @Override
    public void addListener(PathChildrenCacheListener listener) {
        wrapped.getListenable().addListener(listener);

    }

    @Override
    public List<ChildData> getCurrentData() {
        return wrapped.getCurrentData();
    }

    @Override
    public ChildData getCurrentData(Path absolutePath) {
        return wrapped.getCurrentData(absolutePath.getAbsolute());
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
