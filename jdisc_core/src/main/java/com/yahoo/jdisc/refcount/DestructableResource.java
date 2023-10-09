// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

public interface DestructableResource extends AutoCloseable {

    /**
     * Wrapper to allow access to protected AbstractResource.destroy()
     */
    @Override
    void close();
}
