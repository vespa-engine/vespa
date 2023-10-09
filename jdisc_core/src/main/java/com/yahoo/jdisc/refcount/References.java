// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

import com.yahoo.jdisc.ResourceReference;

/**
 * Interface for implementations of reference counting
 * @author baldersheim
 */
public interface References {
    /** Release the initial reference */
    void release();
    /** Returns number of held references */
    int referenceCount();
    /**
     *  Adds a reference and return an objects that when closed will return the reference.
     *  Supply a context that can provide link to the one holding the link. Useful for debugging
     */
    ResourceReference refer(Object context);

    String currentState();
}
