// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import java.util.Optional;

/**
 * Factory for transactional ZooKeeper operations.
 * This mirrors the operations which are actually available in Curator, which unfortunately does not include
 * convenient variants that deletes children, creates parents etc.
 *
 * @author lulf
 * @author bratseth
 */
public class CuratorOperations {

    /**
     * Sets data at this path.
     *
     * @throws IllegalStateException in check() if the path does not exist
     */
    public static CuratorOperation setData(String path, byte[] bytes) {
        return new CuratorSetDataOperation(path, bytes);
    }

    /**
     * Creates this path with data.
     *
     * @throws IllegalStateException in check() if the parent does not exist
     */
    public static CuratorOperation create(String path, byte[] bytes) {
        return new CuratorCreateOperation(path, Optional.of(bytes));
    }

    /**
     * Creates this path with no data.
     *
     * @throws IllegalStateException in check() if the parent does not exist
     */
    public static CuratorOperation create(String path) {
        return new CuratorCreateOperation(path, Optional.empty());
    }

    /** 
     * Deletes this path. This does not delete children.
     * 
     * @throws IllegalStateException in check() if the path does not exist.
     */
    public static CuratorOperation delete(String path) {
        return new CuratorDeleteOperation(path);
    }

}
