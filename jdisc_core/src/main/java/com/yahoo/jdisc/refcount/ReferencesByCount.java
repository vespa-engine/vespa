// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.SharedResource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Does reference counting by using atomic counting of references
 * Default in production
 *
 * @author baldersheim
 */
public class ReferencesByCount implements References {
    private final AtomicInteger refCount;
    private final DestructableResource resource;
    private final NoDebugResourceReference initialReference;

    public ReferencesByCount(DestructableResource resource) {
        refCount = new AtomicInteger(1);
        this.resource = resource;
        initialReference = new NoDebugResourceReference(this);
    }

    @Override
    public void release() {
        initialReference.close();
    }

    @Override
    public int referenceCount() {
        return refCount.get();
    }

    @Override
    public ResourceReference refer(Object context) {
        addRef(1);
        return new NoDebugResourceReference(this);
    }

    @Override
    public String currentState() {
        return "Active references: " + refCount.get() + "."
                + " Resource reference debugging is turned off. Consider toggling the "
                + SharedResource.SYSTEM_PROPERTY_NAME_DEBUG
                + " system property to get debugging assistance with reference tracking.";
    }

    private void removeRef() {
        int refCount = addRef(-1);
        if (refCount == 0) {
            resource.close();
        }
    }

    private int addRef(int value) {
        while (true) {
            int prev = refCount.get();
            if (prev == 0) {
                throw new IllegalStateException(getClass().getName() + ".addRef(" + value + "):"
                        + " Object is already destroyed."
                        + " Consider toggling the " + SharedResource.SYSTEM_PROPERTY_NAME_DEBUG
                        + " system property to get debugging assistance with reference tracking.");
            }
            int next = prev + value;
            if (refCount.compareAndSet(prev, next)) {
                return next;
            }
        }
    }

    private static class NoDebugResourceReference extends CloseableOnce {
        private final ReferencesByCount resource;

        NoDebugResourceReference(final ReferencesByCount resource) {
            this.resource = resource;
        }

        @Override final void onClose() { resource.removeRef(); }
        @Override References getReferences() { return resource; }
    }
}
