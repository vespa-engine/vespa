// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

import com.yahoo.jdisc.ResourceReference;

import java.util.HashMap;
import java.util.Map;

/**
 * Does reference counting by putting a unique key together with optional context in map
 * Used if system property jdisc.debug.resources=simple/true
 *
 * @author baldersheim
 */
public class DebugReferencesByContextMap implements References {
    private final Map<Object, Object> contextMap = new HashMap<>();
    private final DestructableResource resource;
    private final Reference initialReference;
    private long contextId = 1;

    public DebugReferencesByContextMap(DestructableResource resource, Object context) {
        this.resource = resource;
        Long key = 0L;
        initialReference = new Reference(this, key);
        contextMap.put(key, context);
    }

    @Override
    public void release() {
        initialReference.close();
    }

    @Override
    public int referenceCount() {
        synchronized (contextMap) { return contextMap.size(); }
    }

    @Override
    public ResourceReference refer(Object context) {
        synchronized (contextMap) {
            if (contextMap.isEmpty()) {
                throw new IllegalStateException("Object is already destroyed, no more new references may be created."
                        + " State={ " + currentState() + " }");
            }
            Long key = contextId++;
            contextMap.put(key, context != null ? context : key);
            return new Reference(this, key);
        }
    }

    private void removeRef(Long key) {
        synchronized (contextMap) {
            contextMap.remove(key);
            if (contextMap.isEmpty()) {
                resource.close();
            }
        }
    }

    @Override
    public String currentState() {
        synchronized (contextMap) {
            return contextMap.toString();
        }
    }

    private static class Reference extends CloseableOnce {
        private final DebugReferencesByContextMap references;
        private final Long key;

        Reference(DebugReferencesByContextMap references, Long key) {
            this.references = references;
            this.key = key;
        }

        @Override final void onClose() { references.removeRef(key); }
        @Override
        final References getReferences() { return references; }
    }
}
