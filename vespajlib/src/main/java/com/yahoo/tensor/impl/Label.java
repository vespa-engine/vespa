// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.google.common.collect.MapMaker;

import java.util.concurrent.ConcurrentMap;

/**
 * A label is a value of a mapped dimension of a tensor.
 * This class provides a mapping of labels to numbers which allow for more efficient computation with
 * mapped tensor dimensions.
 *
 * @author baldersheim
 */
public class Label {
    private final String string;
    private final long numeric;

    Label(String string) {
        this.string = string;
        this.numeric = 0;
    }

    Label(long numeric) {
        this.string = null;
        this.numeric = numeric;
    }

    public String getString() {
        return string;
    }

    public long getNumeric() {
        return numeric;
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

    private static final String[] SMALL_INDEXES = createSmallIndexesAsStrings(1000);

    private static String[] createSmallIndexesAsStrings(int count) {
        String[] asStrings = new String[count];

        for (int i = 0; i < count; i++) {
            asStrings[i] = String.valueOf(i);
        }

        return asStrings;
    }

    private static final ConcurrentMap<String, Label> stringToLabel = new MapMaker()
            .concurrencyLevel(1).weakValues().makeMap();
    
    public static Label of(String string) {
            
    }
    
    public static Label of(long numeric) {
        
    }
}
//    
//    private static final String[] SMALL_INDEXES = createSmallIndexesAsStrings(1000);
//
//    // Map from String labels to Long values with weak references to values
//    private static final ConcurrentMap<String, Id> stringToIdMap = new MapMaker()
//            .concurrencyLevel(1)
//            .weakValues()
//            .makeMap();
//
//    // Index 0 is unused, that is a valid positive number
//    // 1(-1) is reserved for the Tensor.INVALID_INDEX
//    static {
//        add("UNIQUE_UNUSED_MAGIC", 0);
//        add("Tensor.INVALID_INDEX", -Tensor.invalidIndex);
//    }
//
//    private static final AtomicLong uniqueCounter = new AtomicLong(-2);
//
//    private static void add(String s, long l) {
//        stringToIdMap.put(s, new Id(l));
//        idToStringMap.put(new Id(l), s);
//    }
//    
//    private static String[] createSmallIndexesAsStrings(int count) {
//        String[] asStrings = new String[count];
//        
//        for (int i = 0; i < count; i++) {
//            asStrings[i] = String.valueOf(i);
//        }
//        
//        return asStrings;
//    }
//    
//    private static String asNumericString(long index) {
//        return ((index >= 0) && (index < SMALL_INDEXES.length)) ? SMALL_INDEXES[(int)index] : String.valueOf(index);
//    }
//
//    private static boolean validNumericIndex(String s) {
//        if (s.isEmpty() || ((s.length() > 1) && (s.charAt(0) == '0'))) return false;
//        for (int i = 0; i < s.length(); i++) {
//            char c = s.charAt(i);
//            if ((c < '0') || (c > '9')) return false;
//        }
//        return true;
//    }
//
//    private static Id addNewUniqueString(String s) {
//        var newLong = new Id(uniqueCounter.getAndDecrement());
//        var existingLong = stringToIdMap.putIfAbsent(s, newLong);
//
//        if (existingLong == null) {
//            // This thread inserted new value.
//            idToStringMap.put(newLong, s);
//            return newLong;
//        } else {
//            // Another thread inserted new value.
//            return existingLong;
//        }
//    }
//
//
//    public static Id toNumber(String s) {
//        if (s == null) { 
//            return new Id(Tensor.invalidIndex); 
//        }
//        
//        try {
//            if (validNumericIndex(s)) {
//                return new Id(Long.parseLong(s, 10));
//            }
//        } catch (NumberFormatException e) {
//            // Continue with map
//        }
//        
//        // stringToLongMap.computeIfAbsent is not used here because new entries to longToStringMap and stringToLongMap
//        // should be added in a specific order to handle concurrency, see addNewUniqueString.
//        var l = stringToIdMap.get(s);
//        return l != null ? l : addNewUniqueString(s);
//     }
//
//    public static String fromNumber(long v) {
//        if (v >= 0) {
//            return asNumericString(v);
//        } else {
//            if (v == Tensor.invalidIndex) { 
//                return null; 
//            }
//            
//            return longToStringMap.get(v);
//        }
//    }

}
