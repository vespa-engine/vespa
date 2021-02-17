// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

/**
 * Thrown if a searcher provides the same name as a phase.
 *
 * @author Tony Vaagenes
 */
@SuppressWarnings("serial")
public class ConflictingNodeTypeException extends RuntimeException {

    public ConflictingNodeTypeException(String message) {
        super(message);
    }

}
