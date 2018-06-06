// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;

/**
 * No-op update adapter which simply passes through the input update unchanged.
 * I.e. getOutput() will return a DocumentUpdate containing only the FieldPathUpdate
 * the IdentityFieldPathUpdateAdapter was created with. All other applicable calls are
 * forwarded to the provided DocumentAdapter instance.
 *
 * This removes the need for a potentially lossy round-trip of update -&gt; synthetic document -&gt; update.
 */
public class IdentityFieldPathUpdateAdapter implements UpdateAdapter {

    private final FieldPathUpdate update;
    private final DocumentAdapter fwdAdapter;

    public IdentityFieldPathUpdateAdapter(FieldPathUpdate update, DocumentAdapter fwdAdapter) {
        this.update = update;
        this.fwdAdapter = fwdAdapter;
    }

    @Override
    public DocumentUpdate getOutput() {
        Document doc = fwdAdapter.getFullOutput();
        DocumentUpdate upd = new DocumentUpdate(doc.getDataType(), doc.getId());
        upd.addFieldPathUpdate(update);
        return upd;
    }

    @Override
    public Expression getExpression(Expression expression) {
        return expression;
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return fwdAdapter.getInputValue(fieldName);
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        return fwdAdapter.getInputValue(fieldPath);
    }

    @Override
    public FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        return fwdAdapter.setOutputValue(exp, fieldName, fieldValue);
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return fwdAdapter.getInputType(exp, fieldName);
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        fwdAdapter.tryOutputType(exp, fieldName, valueType);
    }
}
