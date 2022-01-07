// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

/**
 * A pair of values.
 *
 * This class is to avoid littering a class with thin wrapper objects for
 * passing around e.g. the state of an operation and the result value. Using
 * this class may be correct, but it is a symptom that you may want to redesign
 * your code. (Should you pass mutable objects to the method instead? Create a
 * new class and do the work inside that class instead? Etc.)
 *
 * @author Steinar Knutsen
 */
public final class Tuple2<T1, T2> {

    public final T1 first;
    public final T2 second;

    public Tuple2(final T1 first, final T2 second) {
        this.first = first;
        this.second = second;
    }

    /**
     * hashCode() will always throw UnsupportedOperationException. The reason is
     * this class is not meant for being put in Container implementation or
     * similar use where Java generics will lead to a type unsafe maintenance
     * nightmare.
     *
     * @throws UnsupportedOperationException
     *             will always throw this when invoked
     */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException(
                "com.yahoo.collections.Tuple2<T1, T2> does not support equals(Object) by design. Refer to JavaDoc for details.");
    }

    /**
     * equals(Object) will always throw UnsupportedOperationException. The
     * intention is always using the objects contained in the tuple directly.
     *
     * @param obj
     *            ignored
     * @throws UnsupportedOperationException
     *             will always throw this when invoked
     */
    @Override
    public boolean equals(final Object obj) {
        throw new UnsupportedOperationException(
                "com.yahoo.collections.Tuple2<T1, T2> does not support equals(Object) by design. Refer to JavaDoc for details.");
    }

    /**
     * Human readable string representation which invokes the contained
     * instances' toString() implementation.
     */
    @Override
    public String toString() {
        return "Tuple2(" + first + ", " + second + ")";
    }
}
