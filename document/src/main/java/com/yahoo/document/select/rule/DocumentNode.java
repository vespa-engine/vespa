// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentGet;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Visitor;

/**
 * A document node which returns a document: For accessing document field data in AttributeNode,
 * where it should be possible to access fields both by the concrete type ("concreteType.fieldName")
 * and by parent type ("inheritedType.inheritedField").
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DocumentNode implements ExpressionNode {

    private String type;

    public DocumentNode(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public DocumentNode setType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        return evaluate(context.getDocumentOperation());
    }

    private Object evaluate(DocumentOperation op) {
        if (hasData(op))
            return evaluateForDataLookup(op);
        else // Simplify to just false since we can't progress here?
            return op.getId().getDocType().equals(type) ? op : false;
    }

    private Object evaluateForDataLookup(DocumentOperation op) {
        if (op instanceof DocumentPut)
            return ((DocumentPut)op).getDocument().getDataType().isA(this.type) ? op : false;
        else if (op instanceof DocumentUpdate)
            return ((DocumentUpdate)op).getDocumentType().isA(this.type) ? op : false;
        else
            throw new IllegalStateException("Programming error");
    }

    /** Returns whether this operation is of a type that may contain data */
    private boolean hasData(DocumentOperation op) {
        return op instanceof DocumentPut || op instanceof DocumentUpdate;
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return type;
    }

}
