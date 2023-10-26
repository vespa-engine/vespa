// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.DocumentId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Visitor;

/**
 * @author Simon Thoresen Hult
 */
public class IdNode implements ExpressionNode {

    private String field;
    private short widthBits = -1;
    private short divisionBits = -1;

    public IdNode() {
        // empty
    }

    public String getField() {
        return field;
    }

    public IdNode setField(String field) {
        this.field = field;
        return this;
    }

    public IdNode setWidthBits(short widthBits) {
        this.widthBits = widthBits;
        return this;
    }

    public IdNode setDivisionBits(short divisionBits) {
        this.divisionBits = divisionBits;
        return this;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        DocumentId id = context.getDocumentOperation().getId();
        if (id == null) {
            throw new IllegalStateException("Document has no identifier.");
        }
        if (field == null) {
            return id.toString();
        } else if (field.equalsIgnoreCase("scheme")) {
            return id.getScheme().getType().toString();
        } else if (field.equalsIgnoreCase("namespace")) {
            return id.getScheme().getNamespace();
        } else if (field.equalsIgnoreCase("specific")) {
            return id.getScheme().getNamespaceSpecific();
        } else if (field.equalsIgnoreCase("group")) {
            if (id.getScheme().hasGroup()) {
                return id.getScheme().getGroup();
            }
            throw new IllegalStateException("Group identifier is null."); 
        } else if (field.equalsIgnoreCase("user")) {
            if (id.getScheme().hasNumber()) {
                return id.getScheme().getNumber();
            }
            throw new IllegalStateException("User identifier is null.");
        } else if (field.equalsIgnoreCase("type")) {
            if (id.getScheme().hasDocType()) {
                return id.getScheme().getDocType();
            }
            throw new IllegalStateException("Document id doesn't have doc type.");
        } else {
            throw new IllegalStateException("Identifier field '" + field + "' is not supported.");
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "id" + (field != null ? "." + field : "") + (widthBits != -1 ? "(" + widthBits + "," + divisionBits + ")" : "");
    }
}
