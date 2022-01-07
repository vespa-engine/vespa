// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utilities for java collections
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class CollectionUtil {

    /**
     * Returns a String containing the string representation of all elements from
     * the given collection, separated by the separator string.
     *
     * @param collection The collection
     * @param sep The separator string
     * @return A string: elem(0) + sep + ... + elem(N)
     */
    public static String mkString(Collection<?> collection, String sep) {
        return mkString(collection, "", sep, "");
    }

    /**
     * Returns a String containing the string representation of all elements from
     * the given collection, using a start string, separator strings, and an end string.
     *
     * @param collection The collection
     * @param start The start string
     * @param sep The separator string
     * @param end The end string
     * @param <T> The element type
     * @return A string: start + elem(0) + sep + ... + elem(N) + end
     */
    public static <T> String mkString(Collection<T> collection, String start, String sep, String end) {
        return collection.stream()
            .map(T::toString)
            .collect(Collectors.joining(sep, start, end));
     }

    /**
     * Returns true if the contents of the two given collections are equal, ignoring order.
     */
    public static boolean equalContentsIgnoreOrder(Collection<?> c1, Collection<?> c2) {
        return c1.size() == c2.size() &&
                c1.containsAll(c2);
    }

    /**
     * Returns the symmetric difference between two collections, i.e. the set of elements
     * that occur in exactly one of the collections.
     */
    public static <T> Set<T> symmetricDifference(Collection<? extends T> c1, Collection<? extends T> c2) {
        Set<T> diff1 = new HashSet<>(c1);
        diff1.removeAll(c2);

        Set<T> diff2 = new HashSet<>(c2);
        diff2.removeAll(c1);

        diff1.addAll(diff2);
        return diff1;
    }

    /**
     * Returns the subset of elements from the given collection that can be cast to the reference
     * type, defined by the given Class object.
     */
    public static <T> Collection<T> filter(Collection<?> collection, Class<T> lowerBound) {
        List<T> result = new ArrayList<>();
        for (Object element : collection) {
            if (lowerBound.isInstance(element)) {
                result.add(lowerBound.cast(element));
            }
        }
        return result;
    }

    /**
     * Returns the first element in a collection according to iteration order.
     * Returns null if the collection is empty.
     */
    public static <T> T first(Collection<T> collection) {
        return collection.isEmpty()? null: collection.iterator().next();
    }

    public static <T> Optional<T> firstMatching(T[] array, Predicate<? super T> predicate) {
        for (T t: array) {
            if (predicate.test(t))
                return Optional.of(t);
        }
        return Optional.empty();
    }

}
