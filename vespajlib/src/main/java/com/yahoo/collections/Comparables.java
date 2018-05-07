// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.collections;

/**
 * Utilities for {@link Comparable} classes.
 *
 * @author hakon
 */
public class Comparables {
    /**
     * Returns the least element, or {@code first} if they are equal according to
     * {@link Comparable#compareTo(Object) compareTo}.
     */
    public static <T extends Comparable<? super T>> T min(T first, T second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    /**
     * Returns the least element, or {@code second} if they are equal according to
     * {@link Comparable#compareTo(Object) compareTo}.
     */
    public static <T extends Comparable<? super T>> T max(T first, T second) {
        return first.compareTo(second) <= 0 ? second : first;
    }
}
