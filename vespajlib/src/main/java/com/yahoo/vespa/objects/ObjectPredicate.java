// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * A predicate that is able to say either true or false when presented with a
 * generic object.
 *
 * @author Simon Thoresen Hult
 */
public interface ObjectPredicate {

    /**
     * Apply this predicate to the given object.
     *
     * @param obj The object to check.
     * @return True or false.
     */
    boolean check(Object obj);

}
