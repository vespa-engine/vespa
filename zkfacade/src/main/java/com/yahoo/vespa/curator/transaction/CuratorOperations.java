// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Factory for transactional ZooKeeper operations.
 * This mirrors the operations which are actually available in Curator, which unfortunately does not include
 * variants that deletes children, creates parents etc. in a single operation.
 *
 * @author Ulf Lilleengen
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

    /**
     * Returns operations deleting this path and everything below it, in an order where a parent
     * is ordered after all its children,
     * such that the operations will succeed when executed in the returned order.
     * This does not fail, but returns an empty list if the path does not exist.
     */
    public static List<CuratorOperation> deleteAll(String path, Curator curator) {
        if ( ! curator.exists(Path.fromString(path))) return Collections.emptyList();

        List<CuratorOperation> operations = new ArrayList<>();
        deleteRecursively(Path.fromString(path), operations, curator);
        return operations;
    }
    
    private static void deleteRecursively(Path path, List<CuratorOperation> operations, Curator curator) {
        for (String childName : curator.getChildren(path))
            deleteRecursively(path.append(childName), operations, curator);
        operations.add(delete(path.getAbsolute()));
    }

}
