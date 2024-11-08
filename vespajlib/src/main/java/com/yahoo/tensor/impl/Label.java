// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.google.common.collect.MapMaker;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A label is a value of a mapped dimension of a tensor.
 * This class provides a mapping of labels to numbers which allow for more efficient computation with
 * mapped tensor dimensions.
 *
 * @author baldersheim
 */
public class Label {
    private final long numeric;
    private String string = null;
    
    private Label(long numeric) {
        this.numeric = numeric;
    }

    private Label(long numeric, String string) {
        this.numeric = numeric;
        this.string = string;
    }

    public long toNumeric() {
        return numeric;
    }
    
    public String toString() {
        if (string == null) {
            string = String.valueOf(numeric);
        }
        
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var label = (Label) o;
        return numeric == label.numeric;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(numeric);
    }
    
    public static final Label INVALID_INDEX_LABEL = new Label(-1, null);
    public static final Label[] SMALL_INDEX_LABELS = creatSmallIndexLabels(1000);
    
    private static Label[] creatSmallIndexLabels(int count) {
        var labels = new Label[count];
        
        for (var i = 0; i < count; i++) {
            labels[i] = new Label(i, String.valueOf(i));
        }
        
        return labels;
    }
    
    private static final ConcurrentMap<String, Label> byString = new MapMaker()
            .concurrencyLevel(1).weakValues().makeMap();

    private static final ConcurrentMap<Long, Label> byNumeric = new MapMaker()
            .concurrencyLevel(1).weakValues().makeMap();

    private static final AtomicLong idCounter = new AtomicLong(-2);
    
    private static Label add(long numeric, String string) {
        var newLabel = new Label(numeric, string);
        var existingLabel = byString.putIfAbsent(string, newLabel);

        if (existingLabel != null) {
            return existingLabel;
        }

        byNumeric.put(newLabel.numeric, newLabel);
        return newLabel;
    }

    private static boolean validNumericIndex(String s) {
        if (s.isEmpty() || ((s.length() > 1) && (s.charAt(0) == '0'))) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0') || (c > '9')) return false;
        }
        return true;
    }
    
    public static Label of(String string) {
        if (string == null) {
            return INVALID_INDEX_LABEL;
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
        
        // Non-index labels are cached.
        var existingLabel = byString.get(string);
        
        if (existingLabel != null) {
            return existingLabel;
        }
        
        var numeric = idCounter.getAndDecrement();
        return add(numeric, string);
    }

    public static Label of(long numeric) {
        // Positive numeric labels are indexes.
        // They are not cached, but rather pre-computed for small values or created on demand.
        if (numeric >= 0) {
            if (numeric < SMALL_INDEX_LABELS.length) {
                return SMALL_INDEX_LABELS[(int) numeric];
            }
            
            return new Label(numeric);
        }
        
        if (numeric == INVALID_INDEX_LABEL.numeric) {
            return INVALID_INDEX_LABEL;
        }
        
        // Negative numeric labels are mapped to string labels.
        // They are cached.
        var existingLabel = byNumeric.get(numeric);
        
        if (existingLabel != null) {
            return existingLabel;
        }
        
        throw new IllegalArgumentException("No negative numeric label " + numeric);
    }
}
