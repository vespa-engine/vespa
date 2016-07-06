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
    
    CuratorDeleteOperation(String path) {
        this.path = path;
    }

    @Override
    public void check(Curator curator, TransactionChanges changes) {
        // TODO: Check children
        if (  ! curator.exists(Path.fromString(path)) && ! changes.creates(path))
            throw new IllegalStateException("Cannot perform " + this + ": Path does not exist");
        changes.addDeletes(path);
    }

    @Override
    public CuratorTransaction and(CuratorTransaction transaction) throws Exception {
        return transaction.delete().forPath(path).and();
    }

    @Override
    public String toString() {
        return "DELETE " + path;
    }

}
