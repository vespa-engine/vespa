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

    CuratorDeleteOperation(String path, boolean throwIfNotExist) {
        this.path = path;
        this.throwIfNotExist = throwIfNotExist;
    }

    @Override
    public void check(Curator curator) {
        if ( throwIfNotExist && ! curator.exists(Path.fromString(path)) )
            throw new IllegalStateException("Cannot perform " + this + ": Path does not exist");
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
