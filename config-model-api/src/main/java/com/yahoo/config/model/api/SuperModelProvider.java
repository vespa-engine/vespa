// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

public interface SuperModelProvider {
    /**
     * Synchronously call {@link SuperModelListener#applicationActivated(SuperModel, ApplicationInfo)
     * listener.applicationActivated()} on all active applications, and register the listener for future changes
     * to the super model.
     *
     * WARNING: The listener may be called asynchronously before the method returns.
     */
    void registerListener(SuperModelListener listener);

    SuperModel getSuperModel();
}
