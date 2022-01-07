// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * An operation that is able to operate on a generic object.
 *
 * @author Simon Thoresen Hult
 */
public interface ObjectOperation {

    /**
     * Apply this operation to the given object.
     *
     * @param obj The object to operate on.
     */
    void execute(Object obj);

}
