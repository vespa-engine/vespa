// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * @author freva
 */
public interface NodeAgentContextSupplier {

    /**
     * Blocks until the next context is ready
     * @return context
     * @throws ContextSupplierInterruptedException if {@link #interrupt()} was called before this method returned
     */
    NodeAgentContext nextContext() throws ContextSupplierInterruptedException;

    /** Interrupts the thread(s) currently waiting in {@link #nextContext()} */
    void interrupt();

    class ContextSupplierInterruptedException extends RuntimeException { }
}
