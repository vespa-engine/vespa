// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import java.util.HashSet;
import java.util.Set;

/**
 * Records the set of changes which will happen as part of a transaction
 * 
 * @author bratseth
 */
public class TransactionChanges {
    
    /** The set of absolute paths created by this */
    private final Set<String> createdPaths = new HashSet<>();

    /** The set of absolute paths deleted by this */
    private final Set<String> deletedPaths = new HashSet<>();
    
    /** Returns whether the changes include creating this absolute path */
    public boolean create(String path) {
        return createdPaths.contains(path);
    }

    /** Returns whether the changes include creating any (proper) children of the given path */
    public boolean createsChildrenOf(String parentPath) {
        if ( ! parentPath.endsWith("/")) 
            parentPath = parentPath + "/";
        for (String createdPath : createdPaths)
            if (createdPath.startsWith(parentPath))
                return true;
        return false;
    }

    /** Adds creation of an absolute path to the set of changes made by this */
    public void addCreate(String path) {
        deletedPaths.remove(path);
        createdPaths.add(path);
    }

    /** Returns whether the changes include deleting this absolute path */
    public boolean delete(String path) {
        return deletedPaths.contains(path);
    }

    /** Adds deletion of an absolute path to the set of changes made by this */
    public void addDelete(String path) {
        createdPaths.remove(path);
        deletedPaths.add(path);
    }

    @Override
    public String toString() {
        return "Transaction changes: CREATES " + createdPaths + " DELETES " + deletedPaths;
    }
    
}
