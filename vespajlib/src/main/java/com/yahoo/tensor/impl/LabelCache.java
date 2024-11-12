// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.google.common.util.concurrent.Striped;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

/**
 * Cache for string labels so they can be mapped to unique numeric keys.
 * It uses weak references for automatic cleanup of labels that are not used.
 * 
 * @author baldersheim, glebashnik
 */
public class LabelCache {
    // Stores string and numeric keys to clean the cache after Label is garbage collected.
    static class LabelWeakReference extends WeakReference<Label> {
        final String stringKey;
        final long numericKey;

        LabelWeakReference(Label label, ReferenceQueue<Label> referenceQueue) {
            super(label, referenceQueue);
            this.stringKey = label.toString();
            this.numericKey = label.toNumeric();
        }
    }
    
    // Caches labels by string and numeric keys.
    private static final ConcurrentMap<String, LabelWeakReference> byString = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, LabelWeakReference> byNumeric = new ConcurrentHashMap<>();
    
    // Used to generate unique numeric keys for string labels.
    private static final AtomicLong uniqueCounter = new AtomicLong(-2);

    // Used to lock part of the label string space.
    // Number of stripes is proportional to the number of threads expected to create labels concurrently.
    private static final int NUMBER_OF_STRIPES = 32;
    private static final Striped<Lock> stripedLock = Striped.lock(NUMBER_OF_STRIPES);

    // Used to remove garbage collected labels.
    private static final ReferenceQueue<Label> referenceQueue = new ReferenceQueue<>();
    // Pre-computed labels for small indexes.
    public static final Label[] SMALL_INDEX_LABELS = createSmallIndexLabels(1000);
    
    private static Label[] createSmallIndexLabels(int count) {
        var labels = new Label[count];

        for (var i = 0; i < count; i++) {
            labels[i] = new Label(i, String.valueOf(i));
        }

        return labels;
    }

    public static Label getOrCreateLabel(String string) {
        if (string == null) {
            return Label.INVALID_INDEX_LABEL;
        }

        // Index labels are not cached.
        // They are not cached, but rather pre-computed for small values or created on demand.
        if (validNumericIndex(string)) {
            try {
                var numeric = Long.parseLong(string, 10);

                if (numeric < SMALL_INDEX_LABELS.length) {
                    return SMALL_INDEX_LABELS[(int) numeric];
                }

                return new Label(numeric, string);
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

    public static Label getOrCreateLabel(long numeric) {
        // Positive numeric labels are indexes.
        // They are not cached, but rather pre-computed for small values or created on demand.
        if (numeric >= 0) {
            if (numeric < SMALL_INDEX_LABELS.length) {
                return SMALL_INDEX_LABELS[(int) numeric];
            }

            return new Label(numeric);
        }

        if (numeric == Label.INVALID_INDEX_LABEL.toNumeric()) {
            return Label.INVALID_INDEX_LABEL;
        }

        // Negative numeric labels are mapped to string labels.
        var existingLabel = getLabel(numeric);

        if (existingLabel != null) {
            return existingLabel;
        }

        throw new IllegalArgumentException("No negative numeric label " + numeric);
    }
    
    private static boolean validNumericIndex(String s) {
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

    private static Label getLabel(long numeric) {
        var weakReference = byNumeric.get(numeric);
        return weakReference != null ? weakReference.get() : null;
    }

    private static Label getLabel(String string) {
        var weakReference = byString.get(string);
        return weakReference != null ? weakReference.get() : null;
    }
    
    private static Label createLabel(String string) {
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
            var newLabel = new Label(newNumeric, string);
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

    private static void removeStaleReferences() {
        LabelWeakReference staleReference;
        while ((staleReference = (LabelWeakReference) referenceQueue.poll()) != null) {
            // No lock needed because concurrent map checks that the value has not changed.
            // i.e. if another thread replaced the value, it will not be removed.
            byString.remove(staleReference.stringKey, staleReference);
            byNumeric.remove(staleReference.numericKey, staleReference);
        }
    }
}
