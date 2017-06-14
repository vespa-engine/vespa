// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * This class keeps some information from the access log from the requests in memory. It is thread-safe.
 *
 * @author dybis
 */
public class CircularArrayAccessLogKeeper {
    public static final int SIZE = 1000;
    private final Deque<String> uris = new ArrayDeque<>(SIZE);
    private final Object monitor = new Object();

    /**
     *  This class is intended to be used with injection so it can be shared between other classes.
     */
    public CircularArrayAccessLogKeeper() {}

    /**
     * Creates a list of Uris.
     * @return URIs as string
     */
    public List<String> getUris() {
        final List<String> uriList = new ArrayList<>();
        synchronized (monitor) {
            uris.iterator().forEachRemaining(uri -> uriList.add(uri));
        }
        return uriList;
    }

    /**
     * Add a new URI. It might remove an old entry to make space for new entry.
     * @param uri uri as string
     */
    public void addUri(String uri) {
        synchronized (monitor) {
            if (uris.size() == SIZE) {
                uris.pop();
            }
            uris.add(uri);
        }
    }
}
