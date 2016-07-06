// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

/**
 * @author lulf
 * @author bratseth
 */
class CuratorDeleteOperation implements CuratorOperation {

    private final String path;
    private final boolean throwIfNotExist;
    
    /** False iff we positively know this path does not exist */
    private boolean pathExists = true;

    CuratorDeleteOperation(String path, boolean throwIfNotExist) {
        this.path = path;
        this.throwIfNotExist = throwIfNotExist;
    }

    @Override
    public void check(Curator curator, TransactionChanges changes) {
        // TODO: Check children
        pathExists = curator.exists(Path.fromString(path)) || changes.creates(path);
        if ( throwIfNotExist && ! pathExists)
            throw new IllegalStateException("Cannot perform " + this + ": Path does not exist");
        if ( ! pathExists)
            changes.addDeletes(path);
    }

    @Override
    public CuratorTransaction and(CuratorTransaction transaction) throws Exception {
        System.out.println("path: " + path + ", exists: " + pathExists);
        if ( ! throwIfNotExist && ! pathExists) return transaction; // this is a noop
        return transaction.delete().forPath(path).and();
    }

    @Override
    public String toString() {
        return "DELETE " + path;
    }

}
