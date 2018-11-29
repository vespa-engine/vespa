// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

public interface SuperModelProvider {
    SuperModel getSuperModel();

    /**
     * Returns the current SuperModel. All changes to the SuperModel
     * following that snapshot will be published to the listener. Warning: The listener
     * methods may have been invoked before (or concurrently with) this method returning.
     */
    SuperModel snapshot(SuperModelListener listener);

}
