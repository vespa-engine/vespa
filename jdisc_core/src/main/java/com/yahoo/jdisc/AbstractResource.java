// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.refcount.DebugReferencesByContextMap;
import com.yahoo.jdisc.refcount.DebugReferencesWithStack;
import com.yahoo.jdisc.refcount.DestructableResource;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jdisc.refcount.References;

/**
 * This class provides a thread-safe implementation of the {@link SharedResource} interface, and should be used for
 * all subclasses of {@link RequestHandler}, {@link ClientProvider} and {@link ServerProvider}. Once the reference count
 * of this resource reaches zero, the {@link #destroy()} method is called.
 *
 * @author Simon Thoresen Hult
 */
public abstract class AbstractResource implements SharedResource {

    private static final Debug debug = DEBUG;

    private final References references;

    protected AbstractResource() {
        DestructableResource destructable = new WrappedResource(this);
        if (debug == Debug.STACK) {
            references = new DebugReferencesWithStack(destructable);
        } else {
            references = new DebugReferencesByContextMap(destructable, this);
        }
    }

    @Override
    public final ResourceReference refer() {
        return refer(null);
    }
    @Override
    public final ResourceReference refer(Object context) {
        return references.refer(context);
    }

    @Override
    public final void release() {
        references.release();
    }

    /**
     * <p>Returns the reference count of this resource. This typically has no value for other than single-threaded unit-
     * tests, as it is merely a snapshot of the counter.</p>
     *
     * @return The current value of the reference counter.
     */
    public final int retainCount() {
        return references.referenceCount();
    }

    /**
     * <p>This method signals that this AbstractResource can dispose of any internal resources, and commence with shut
     * down of any internal threads. This will be called once the reference count of this resource reaches zero.</p>
     */
    protected void destroy() { }

    /**
     * Returns a string describing the current state of references in human-friendly terms. May be used for debugging.
     */
    public String currentState() {
        return references.currentState();
    }

    static private class WrappedResource implements DestructableResource {
        private final AbstractResource resource;
        WrappedResource(AbstractResource resource) { this.resource = resource; }
        @Override public void close() { resource.destroy(); }
    }
}
