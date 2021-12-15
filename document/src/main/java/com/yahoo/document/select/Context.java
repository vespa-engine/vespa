// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.DocumentOperation;

import java.util.HashMap;
import java.util.Map;

public class Context {

    private DocumentOperation documentOperation;
    private Map<String, Object> variables = new HashMap<>();

    public Context(DocumentOperation documentOperation) {
        this.documentOperation = documentOperation;
    }

    public DocumentOperation getDocumentOperation() {
        return documentOperation;
    }

    public void setDocumentOperation(DocumentOperation documentOperation) {
        this.documentOperation = documentOperation;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
