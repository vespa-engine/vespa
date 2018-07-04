// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * This is an abstract class used to visit structured objects. It contains a basic interface that is intended to be
 * overridden by subclasses. As an extension to this class, the visit.hpp file contains various versions of the visit
 * method that maps visitation of various types into invocations of the basic interface defined by this class.
 *
 * @author Simon Thoresen Hult
 */
public abstract class ObjectVisitor {

    /**
     * Open a (sub-)structure
     *
     * @param name name of structure
     * @param type type of structure
     */
    public abstract void openStruct(String name, String type);

    /**
     * Close a (sub-)structure
     */
    public abstract void closeStruct();

    /**
     * Visits some object.
     *
     * @param name variable name
     * @param obj object to visit
     */
    public abstract void visit(String name, Object obj);
}
