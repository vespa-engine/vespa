// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides a thread-safe implementation of the {@link SharedResource} interface, and should be used for
 * all subclasses of {@link RequestHandler}, {@link ClientProvider} and {@link ServerProvider}. Once the reference count
 * of this resource reaches zero, the {@link #destroy()} method is called.
 *
 * @author Simon Thoresen Hult
 */
public abstract class AbstractResource implements SharedResource {

    private static final Logger log = Logger.getLogger(AbstractResource.class.getName());

    private final boolean debug = SharedResource.DEBUG;
    private final AtomicInteger refCount;
    private final Object monitor;
    private final Set<Throwable> activeReferences;
    private final ResourceReference initialCreationReference;

    protected AbstractResource() {
        if (!debug) {
            this.refCount = new AtomicInteger(1);
            this.monitor = null;
            this.activeReferences = null;
            this.initialCreationReference = new NoDebugResourceReference(this);
        } else {
            this.refCount = null;
            this.monitor = new Object();
            this.activeReferences = new HashSet<>();
            final Throwable referenceStack = new Throwable();
            this.activeReferences.add(referenceStack);
            this.initialCreationReference = new DebugResourceReference(this, referenceStack);
        }
    }

    @Override
    public final ResourceReference refer() {
        if (!debug) {
            addRef(1);
            return new NoDebugResourceReference(this);
        }

        final Throwable referenceStack = new Throwable();
        final String state;
        synchronized (monitor) {
            if (activeReferences.isEmpty()) {
                throw new IllegalStateException("Object is already destroyed, no more new references may be created."
                        + " State={ " + currentStateDebugWithLock() + " }");
            }
            activeReferences.add(referenceStack);
            state = currentStateDebugWithLock();
        }
        log.log(Level.WARNING,
                getClass().getName() + "@" + System.identityHashCode(this) + ".refer(): state={ " + state + " }",
                referenceStack);
        return new DebugResourceReference(this, referenceStack);
    }

    @Override
    public void release() {
        initialCreationReference.close();
    }

    private void removeReferenceStack(final Throwable referenceStack, final Throwable releaseStack) {
        final boolean doDestroy;
        final String state;
        synchronized (monitor) {
            final boolean wasThere = activeReferences.remove(referenceStack);
            state = currentStateDebugWithLock();
            if (!wasThere) {
                throw new IllegalStateException("Reference is already released and can only be released once."
                        + " reference=" + Arrays.toString(referenceStack.getStackTrace())
                        + ". State={ " + state + "}");
            }
            doDestroy = activeReferences.isEmpty();
        }
        log.log(Level.WARNING,
                getClass().getName() + "@" + System.identityHashCode(this) + " release: state={ " + state + " }",
                releaseStack);
        if (doDestroy) {
            destroy();
        }
    }

    /**
     * <p>Returns the reference count of this resource. This typically has no value for other than single-threaded unit-
     * tests, as it is merely a snapshot of the counter.</p>
     *
     * @return The current value of the reference counter.
     */
    public final int retainCount() {
        if (!debug) {
            return refCount.get();
        }

        synchronized (monitor) {
            return activeReferences.size();
        }
    }

    /**
     * <p>This method signals that this AbstractResource can dispose of any internal resources, and commence with shut
     * down of any internal threads. This will be called once the reference count of this resource reaches zero.</p>
     */
    protected void destroy() {

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

    /**
     * Returns a string describing the current state of references in human-friendly terms. May be used for debugging.
     */
    public String currentState() {
        if (!debug) {
            return "Active references: " + refCount.get() + "."
                    + " Resource reference debugging is turned off. Consider toggling the "
                    + SharedResource.SYSTEM_PROPERTY_NAME_DEBUG
                    + " system property to get debugging assistance with reference tracking.";
        }
        synchronized (monitor) {
            return currentStateDebugWithLock();
        }
    }

    private String currentStateDebugWithLock() {
        return "Active references: " + makeListOfActiveReferences();
    }

    private String makeListOfActiveReferences() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (final Throwable activeReference : activeReferences) {
            builder.append(" ");
            builder.append(Arrays.toString(activeReference.getStackTrace()));
        }
        builder.append(" ]");
        return builder.toString();
    }

    private static class NoDebugResourceReference implements ResourceReference {
        private final AbstractResource resource;
        private final AtomicBoolean isReleased = new AtomicBoolean(false);

        public NoDebugResourceReference(final AbstractResource resource) {
            this.resource = resource;
        }

        @Override
        public final void close() {
            final boolean wasReleasedBefore = isReleased.getAndSet(true);
            if (wasReleasedBefore) {
                final String message = "Reference is already released and can only be released once."
                        + " State={ " + resource.currentState() + " }";
                throw new IllegalStateException(message);
            }
            int refCount = resource.addRef(-1);
            if (refCount == 0) {
                resource.destroy();
            }
        }
    }

    private static class DebugResourceReference implements ResourceReference {
        private final AbstractResource resource;
        private final Throwable referenceStack;

        public DebugResourceReference(final AbstractResource resource, final Throwable referenceStack) {
            this.resource = resource;
            this.referenceStack = referenceStack;
        }

        @Override
        public final void close() {
            final Throwable releaseStack = new Throwable();
            resource.removeReferenceStack(referenceStack, releaseStack);
        }
    }
}
