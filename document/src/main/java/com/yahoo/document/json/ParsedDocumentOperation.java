// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.yahoo.document.DocumentOperation;

import java.util.Objects;

/**
 * The result of JSON parsing a single document operation
 */
public final class ParsedDocumentOperation {

    private final DocumentOperation operation;
    private final boolean fullyApplied;

    /**
     * @param operation    the parsed operation
     * @param fullyApplied true if all the JSON content could be applied,
     *                     false if some (or all) of the fields were not poresent in this document and was ignored
     */
    public ParsedDocumentOperation(DocumentOperation operation, boolean fullyApplied) {
        this.operation = operation;
        this.fullyApplied = fullyApplied;
    }

    public DocumentOperation operation() { return operation; }

    public boolean fullyApplied() { return fullyApplied; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ParsedDocumentOperation) obj;
        return Objects.equals(this.operation, that.operation) &&
               this.fullyApplied == that.fullyApplied;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, fullyApplied);
    }

    @Override
    public String toString() {
        return "ParsedDocumentOperation[" +
               "operation=" + operation + ", " +
               "fullyApplied=" + fullyApplied + ']';
    }

}
