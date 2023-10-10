// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

/**
 * Constants related to the subquery bitmap in predicate search.
 * 
 * @author bjorncs
 */
public interface SubqueryBitmap {

    /**
     * A feature annotated with the following bitmap will be added to all (64) subqueries.
     */
    long ALL_SUBQUERIES = 0xFFFF_FFFF_FFFF_FFFFl;

    /**
     * Default subquery bitmap.
     */
    long DEFAULT_VALUE = ALL_SUBQUERIES;

}
