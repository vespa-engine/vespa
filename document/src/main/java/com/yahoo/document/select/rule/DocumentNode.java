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
 * @author Simon Thoresen Hult
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

    public Object evaluate(DocumentOperation op) {
        return op.getId().getDocType().equals(type) ? op : false;
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return type;
    }

}
