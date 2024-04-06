// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

/**
 * @author Ulf Lilleengen
 * @author bratseth
 */
class CuratorDeleteOperation implements CuratorOperation {

    private final String path;
    
    CuratorDeleteOperation(String path) {
        this.path = path;
    }

    @Override
    public void check(Curator curator, TransactionChanges changes) {
        if (  ! curator.exists(Path.fromString(path)) && ! changes.create(path))
            throw new IllegalStateException("Cannot perform " + this + ": Path does not exist");
        if (hasNondeletedChildren(Path.fromString(path), curator, changes) || changes.createsChildrenOf(path))
            throw new IllegalStateException("Cannot perform " + this + ": Path is not empty");
        changes.addDelete(path);
    }
    
    private boolean hasNondeletedChildren(Path path, Curator curator, TransactionChanges changes) {
        for (String childName : curator.getChildren(path))
            if ( ! changes.delete(path.append(childName).getAbsolute()))
                return true;
        return false;
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
