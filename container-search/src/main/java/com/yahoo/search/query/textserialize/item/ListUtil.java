// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import java.util.*;

/**
 * @author Tony Vaagenes
 */
public class ListUtil {
    public static <T> List<T> rest(List<T> list) {
        return list.subList(1, list.size());
    }

    public static <T> T first(Collection<T> collection) {
        return collection.iterator().next();
    }

    public static boolean firstInstanceOf(Collection<?> collection, @SuppressWarnings("rawtypes") Class c) {
        return !collection.isEmpty() && c.isInstance(first(collection));
    }

    public static <T> List<T> butFirst(List<T> list) {
        return list.subList(1, list.size());
    }

    public static <T> Iterable<T> butFirst(final Collection<T> collection) {
        return () -> {
            Iterator<T> i = collection.iterator();
            i.next();
            return i;
        };
    }
}
