// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

/**
 * @author Arne Bergene Fossaa
 */
class QueryHelper {

    /** Compares two objects which may be null */
    public static boolean equals(Object a,Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    /**
     * Helper method that finds the hashcode for a group of objects.
     * Inspired by java.util.List
     */
    public static int combineHash(Object... objs) {
       int hash = 1;
       for (Object o:objs) {
           hash = 31*hash + (o == null ? 0 : o.hashCode());
       }
       return hash;
    }

}
