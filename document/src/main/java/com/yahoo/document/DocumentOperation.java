// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * Base class for "document operations".
 * These include "put" (DocumentPut), "update" (DocumentUpdate), "remove" (DocumentRemove)
 * and "get" (DocumentGet).
 *
 * @author Vegard Sjonfjell
 */
public abstract class DocumentOperation {

    private TestAndSetCondition condition = TestAndSetCondition.NOT_PRESENT_CONDITION;

    public abstract DocumentId getId();

    public void setCondition(TestAndSetCondition condition) {
        this.condition = condition;
    }

    public TestAndSetCondition getCondition() {
        return condition;
    }

    protected DocumentOperation() {}

    /**
     * Copy constructor
     * @param other DocumentOperation to copy
     */
    protected DocumentOperation(DocumentOperation other) {
        this.condition = other.condition;
    }

}
