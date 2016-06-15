// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import java.util.Optional;

/**
 * Factory for transactional ZooKeeper operations
 *
 * @author lulf
 */
public class CuratorOperations {

    public static CuratorOperation setData(String path, byte[] bytes) {
        return new CuratorSetDataOperation(path, bytes);
    }

    public static CuratorOperation create(String path, byte[] bytes) {
        return new CuratorCreateOperation(path, Optional.of(bytes));
    }

    public static CuratorOperation create(String path) {
        return new CuratorCreateOperation(path, Optional.empty());
    }

    public static CuratorOperation delete(String path) {
        return new CuratorDeleteOperation(path);
    }

}
