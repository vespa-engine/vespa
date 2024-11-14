// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.google.common.util.concurrent.Striped;
import com.yahoo.tensor.Label;
import com.yahoo.tensor.Tensor;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

/**
 * Cache for string labels so they can be mapped to unique numeric keys.
 * It uses weak references for automatic cleanup of unused labels.
 * 
 * @author baldersheim, glebashnik
 */
public class LabelCache {
    // Global cache used as default.
    public static final LabelCache GLOBAL = new LabelCache(32, 1000);
    // Label for invalid index.
    public static final Label INVALID_INDEX_LABEL = new LabelImpl(Tensor.invalidIndex, null);
    
    // Stores string and numeric keys to clean the cache after Label is garbage collected.
    static class LabelWeakReference extends WeakReference<Label> {
        final String stringKey;
        final long numericKey;

        LabelWeakReference(Label label, ReferenceQueue<Label> referenceQueue) {
            super(label, referenceQueue);
            this.stringKey = label.asString();
            this.numericKey = label.asNumeric();
        }
    }
    
    // Caches labels by string and numeric keys.
    private final ConcurrentMap<String, LabelWeakReference> byString = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, LabelWeakReference> byNumeric = new ConcurrentHashMap<>();
    
    // Used to generate unique numeric keys for string labels.
    private final AtomicLong uniqueCounter = new AtomicLong(-2);

    // Used to lock part of the label string space.
    private final Striped<Lock> stripedLock;

    // Used to remove garbage collected labels.
    private final ReferenceQueue<Label> referenceQueue = new ReferenceQueue<>();
    
    // Pre-computed labels for small numeric labels used for indexed dimensions.
    private final Label[] smallIndex;

    /**
     * Creates a label cache.
     * 
     * @param lockStripes number of lock stripes, should be proportional to the number of threads using the cache.
     * @param smallIndexSize size of a cache used to store pre-computed labels for small index labels.
     */
    LabelCache(int lockStripes, int smallIndexSize) {
        stripedLock = Striped.lock(lockStripes);
        smallIndex = createSmallIndexLabels(smallIndexSize);
    }

    private Label[] createSmallIndexLabels(int count) {
        var labels = new Label[count];

        for (var i = 0; i < count; i++) {
            labels[i] = new LabelImpl(i, String.valueOf(i));
        }

        return labels;
    }

    public Label getOrCreateLabel(String string) {
        if (string == null) {
            return INVALID_INDEX_LABEL;
        }

        // Index labels are not cached.
        // They are not cached, but rather pre-computed for small values or created on demand.
        if (validNumericIndex(string)) {
            try {
                var numeric = Long.parseLong(string, 10);

                if (numeric < smallIndex.length) {
                    return smallIndex[(int) numeric];
                }

                return new LabelImpl(numeric, string);
            } catch(NumberFormatException e){
                // Continue with cached labels
            }
        }
        
        var existingLabel = getLabel(string);
        if (existingLabel != null) {
            return existingLabel;
        }
        
        return createLabel(string);
    }

    public Label getOrCreateLabel(long numeric) {
        // Positive numeric labels are indexes.
        // They are not cached, but rather pre-computed for small values or created on demand.
        if (numeric >= 0) {
            if (numeric < smallIndex.length) {
                return smallIndex[(int) numeric];
            }

            return new LabelImpl(numeric);
        }

        if (numeric == INVALID_INDEX_LABEL.asNumeric()) {
            return INVALID_INDEX_LABEL;
        }

        // Negative numeric labels are mapped to string labels.
        var existingLabel = getLabel(numeric);

        if (existingLabel != null) {
            return existingLabel;
        }

        throw new IllegalArgumentException("No negative numeric label " + numeric);
    }
    
    private boolean validNumericIndex(String s) {
        if (s.isEmpty() || ((s.length() > 1) && (s.charAt(0) == '0'))) {
            return false;
        }
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        
        return true;
    }

    private Label getLabel(long numeric) {
        var weakReference = byNumeric.get(numeric);
        return weakReference != null ? weakReference.get() : null;
    }

    private Label getLabel(String string) {
        var weakReference = byString.get(string);
        return weakReference != null ? weakReference.get() : null;
    }
    
    private Label createLabel(String string) {
        // Need a lock to avoid creating the same label twice if another thread is creating the same label.
        var lock = stripedLock.get(string);
        lock.lock();

        try {
            // Try to get the label in case another thread has already created one while we were waiting for the lock.
            var existingLabel = getLabel(string);
            if (existingLabel != null) {
                return existingLabel;
            }

            var newNumeric = uniqueCounter.getAndDecrement();
            var newLabel = new LabelImpl(newNumeric, string);
            var newReference = new LabelWeakReference(newLabel, referenceQueue);

            byString.put(string, newReference);
            byNumeric.put(newNumeric, newReference);

            return newLabel;
        } finally {
            lock.unlock();
            
            // Remove stale references can be moved into a separate thread if needed.
            removeStaleReferences();
        }
    }

    private void removeStaleReferences() {
        LabelWeakReference staleReference;
        while ((staleReference = (LabelWeakReference) referenceQueue.poll()) != null) {
            // No lock needed because concurrent map checks that the value has not changed.
            // i.e. if another thread replaced the value, it will not be removed.
            byString.remove(staleReference.stringKey, staleReference);
            byNumeric.remove(staleReference.numericKey, staleReference);
        }
    }
    
    public int size() {
        return byString.size();
    }
}
