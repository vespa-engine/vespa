// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.util.Objects;

/**
 * @author freva
 */
public class NodeAgentContextManager implements NodeAgentContextSupplier, NodeAgentScheduler {
    private final Object monitor = new Object();
    private NodeAgentContext currentContext;
    private NodeAgentContext nextContext;
    private boolean wantFrozen = false;
    private boolean isFrozen = true;
    private boolean pendingInterrupt = false;

    public NodeAgentContextManager(NodeAgentContext context) {
        currentContext = context;
    }

    @Override
    public void scheduleTickWith(NodeAgentContext context) {
        synchronized (monitor) {
            nextContext = Objects.requireNonNull(context);
            monitor.notifyAll();
        }
    }

    @Override
    public boolean setFrozen(boolean frozen) {
        synchronized (monitor) {
            if (wantFrozen != frozen) {
                wantFrozen = frozen;
                monitor.notifyAll();
            }

            return isFrozen == frozen;
        }
    }

    @Override
    public NodeAgentContext nextContext() throws InterruptedException {
        synchronized (monitor) {
            isFrozen = true;
            while (nextContext == null) {
                if (pendingInterrupt) {
                    pendingInterrupt = false;
                    throw new InterruptedException("interrupt() was called before next context was scheduled");
                }

                try {
                    monitor.wait();
                } catch (InterruptedException ignored) { }
            }
            isFrozen = false;

            currentContext = nextContext;
            nextContext = null;
            return currentContext;
        }
    }

    @Override
    public NodeAgentContext currentContext() {
        synchronized (monitor) {
            return currentContext;
        }
    }

    @Override
    public void interrupt() {
        synchronized (monitor) {
            pendingInterrupt = true;
            monitor.notifyAll();
        }
    }
}