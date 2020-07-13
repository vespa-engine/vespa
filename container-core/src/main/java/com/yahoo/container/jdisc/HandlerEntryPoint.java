// Copyright 2020 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import java.net.URI;
import java.util.Objects;

/**
 * A class for holding user-readable data about a handler.  A handler will return a set of these to provide
 * a full URL the user can follow to navigate into the handler.  Most handlers will only return one, but some
 * could return two (e.g. provide one entry point per document type).
 *
 * @author ogronnesby
 */
public class HandlerEntryPoint {
    private final URI path;

    private HandlerEntryPoint(URI path) {
        this.path = path;
    }

    public URI path() {
        return path;
    }

    public static HandlerEntryPoint of(String path) {
        return new HandlerEntryPoint(URI.create(path));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandlerEntryPoint that = (HandlerEntryPoint) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return "HandlerEntryPoint{" +
                "path=" + path +
                '}';
    }
}
