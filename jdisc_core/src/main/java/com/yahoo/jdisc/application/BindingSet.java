// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * <p>This is an immutable set of ordered bindings from {@link UriPattern}s to some target type T. To create an instance
 * of this class, you must 1) create a {@link BindingRepository}, 2) configure it using the {@link
 * BindingRepository#bind(String, Object)} method, and finally 3) call {@link BindingRepository#activate()}.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class BindingSet<T> implements Iterable<Map.Entry<UriPattern, T>>  {

    public static final String DEFAULT = "default";
    private final Collection<Map.Entry<UriPattern, T>> bindings;

    BindingSet(Collection<Map.Entry<UriPattern, T>> bindings) {
        this.bindings = sort(bindings);
    }

    /**
     * <p>Resolves the binding that best matches (see commentary on {@link BindingRepository#bind(String, Object)}) the
     * given {@link URI}, and returns a {@link BindingMatch} object that describes the match and contains the
     * matched target. If there is no binding that matches the given URI, this method returns null.</p>
     *
     * @param uri The URI to match against the bindings in this set.
     * @return A {@link BindingMatch} object describing the match found, or null if not found.
     */
    public BindingMatch<T> match(URI uri) {
        for (Map.Entry<UriPattern, T> entry : bindings) {
            UriPattern.Match match = entry.getKey().match(uri);
            if (match != null) {
                return new BindingMatch<>(match, entry.getValue());
            }
        }
        return null;
    }

    /**
     * <p>Resolves the binding that best matches (see commentary on {@link BindingRepository#bind(String, Object)}) the
     * given {@link URI}, and returns that target. If there is no binding that matches the given URI, this method
     * returns null.</p>
     *
     * <p>Apart from a <em>null</em>-guard, this is equal to <code>return match(uri).target()</code>.</p>
     *
     * @param uri The URI to match against the bindings in this set.
     * @return The best matched target, or null.
     * @see #match(URI)
     */
    public T resolve(URI uri) {
        BindingMatch<T> match = match(uri);
        if (match == null) {
            return null;
        }
        return match.target();
    }

    @Override
    public Iterator<Map.Entry<UriPattern, T>> iterator() {
        return bindings.iterator();
    }

    private static <T> Collection<Map.Entry<UriPattern, T>> sort(Collection<Map.Entry<UriPattern, T>> unsorted) {
        List<Map.Entry<UriPattern, T>> ret = new LinkedList<>(unsorted);
        Collections.sort(ret, new Comparator<Map.Entry<UriPattern, ?>>() {

            @Override
            public int compare(Map.Entry<UriPattern, ?> lhs, Map.Entry<UriPattern, ?> rhs) {
                return lhs.getKey().compareTo(rhs.getKey());
            }
        });
        return ImmutableList.copyOf(ret);
    }
}
