// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * @author baldersheim
 *
 * This class acts as an interface for traversing a tree, or a graph.
 * Every non leaf Object implements {@link #selectMembers(ObjectPredicate, ObjectOperation)} implementing
 * the actual traversal. You can then implement an {@link ObjectPredicate} to select which nodes you want to look at with
 * your {@link ObjectOperation}
 */
public class Selectable {

    /**
     * Apply the predicate to this object. If the predicate returns true, pass this object to the operation, otherwise
     * invoke the {@link #selectMembers(ObjectPredicate, ObjectOperation)} method to locate sub-elements that might
     * trigger the predicate.
     *
     * @param predicate component used to select (sub-)objects
     * @param operation component performing some operation on the selected (sub-)objects
     */
    public final void select(ObjectPredicate predicate, ObjectOperation operation) {
        if (predicate.check(this)) {
            operation.execute(this);
        } else {
            selectMembers(predicate, operation);
        }
    }

    /**
     * Invoke {@link #select(ObjectPredicate, ObjectOperation)} on any member objects this object wants to expose
     * through the selection mechanism. Overriding this method is optional, and which objects to expose is determined by
     * the application logic of the object itself.
     *
     * @param predicate component used to select (sub-)objects
     * @param operation component performing some operation on the selected (sub-)objects
     */
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        // empty
    }

    public static void select(Selectable selectable, ObjectPredicate predicate, ObjectOperation operation) {
        if (selectable != null) {
            selectable.select(predicate, operation);
        }
    }
}
