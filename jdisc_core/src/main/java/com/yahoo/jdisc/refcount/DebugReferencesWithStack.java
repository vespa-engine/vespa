// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

import com.yahoo.jdisc.ResourceReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Does reference counting by putting stacktraces in a map together with an optional context.
 * Intended only for debugging as it is slow.
 * Used if system property jdisc.debug.resources=stack
 *
 * @author baldersheim
 */
public class DebugReferencesWithStack implements References {

    private static final Logger log = Logger.getLogger(DebugReferencesWithStack.class.getName());
    private final Map<Throwable, Object> activeReferences = new HashMap<>();
    private final DestructableResource resource;
    private final DebugResourceReference initialreference;

    public DebugReferencesWithStack(DestructableResource resource) {
        final Throwable referenceStack = new Throwable();
        this.activeReferences.put(referenceStack, this);
        this.resource = resource;
        initialreference = new DebugResourceReference(this, referenceStack);
    }

    @Override
    public void release() {
        initialreference.close();
    }

    @Override
    public int referenceCount() {
        synchronized (activeReferences) {
            return activeReferences.size();
        }
    }

    @Override
    public ResourceReference refer(Object context) {
        final Throwable referenceStack = new Throwable();
        synchronized (activeReferences) {
            if (activeReferences.isEmpty()) {
                throw new IllegalStateException("Object is already destroyed, no more new references may be created."
                        + " State={ " + currentState() + " }");
            }
            activeReferences.put(referenceStack, context);
        }
        log.log(Level.FINE, referenceStack, () ->
                getClass().getName() + "@" + System.identityHashCode(this) + ".refer(): state={ " + currentState() + " }");
        return new DebugResourceReference(this, referenceStack);
    }

    private void removeReferenceStack(final Throwable referenceStack, final Throwable releaseStack) {
        final boolean doDestroy;
        synchronized (activeReferences) {
            final boolean wasThere = activeReferences.containsKey(referenceStack);
            activeReferences.remove(referenceStack);
            if (!wasThere) {
                throw new IllegalStateException("Reference is already released and can only be released once."
                        + " reference=" + Arrays.toString(referenceStack.getStackTrace())
                        + ". State={ " + currentState() + "}");
            }
            doDestroy = activeReferences.isEmpty();
            log.log(Level.FINE, releaseStack,
                    () -> getClass().getName() + "@" + System.identityHashCode(this) + " release: state={ " + currentState() + " }");
        }

        if (doDestroy) {
            resource.close();
        }
    }

    @Override
    public String currentState() {
        return "Active references: " + makeListOfActiveReferences();
    }

    private String makeListOfActiveReferences() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        synchronized (activeReferences) {
            for (var activeReference : activeReferences.entrySet()) {
                builder.append(" ");
                builder.append(Arrays.toString(activeReference.getKey().getStackTrace()));
            }
        }
        builder.append(" ]");
        return builder.toString();
    }

    private static class DebugResourceReference extends CloseableOnce {
        private final DebugReferencesWithStack resource;
        private final Throwable referenceStack;

        public DebugResourceReference(DebugReferencesWithStack resource, final Throwable referenceStack) {
            this.resource = resource;
            this.referenceStack = referenceStack;
        }

        @Override
        final void onClose() {
            final Throwable releaseStack = new Throwable();
            resource.removeReferenceStack(referenceStack, releaseStack);
        }
        @Override
        final References getReferences() { return resource; }
    }
}
