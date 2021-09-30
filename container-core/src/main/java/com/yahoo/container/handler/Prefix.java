// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

/**
 * Wrapper to maintain indirections between prefixes and Handler instances.
 *
 * @author Steinar Knutsen
 */
public final class Prefix implements Comparable<Prefix> {

    public final String prefix;
    public final String[] elements;
    public final String handler;

    public Prefix(String prefix, String handler) {
        super();
        this.prefix = prefix;
        this.elements = prefix.split("/");
        this.handler = handler;
    }

    public Prefix(String prefix) {
        this(prefix, null);
    }

    public boolean hasAnyCommonPrefix(String router) {
        return router.codePointAt(0) == prefix.codePointAt(0);
    }

    @Override
    public int compareTo(Prefix other) {
        return prefix.compareTo(other.prefix);
    }

    public boolean matches(String path) {
        String[] pathElements = path.split("/");
        if (pathElements.length < elements.length) {
            return false;
        }
        for (int i = 0; i < elements.length; ++i) {
            if (!(elements[i].equals(pathElements[i]))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return prefix + ": " + handler;
    }

}
