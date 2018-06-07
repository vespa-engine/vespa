// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings("rawtypes")
public class SimpleAdapterFactory implements AdapterFactory {
    public static class SelectExpression {
        public Expression selectExpression(DocumentType documentType, String fieldName) {
            return null;
        }
    }
    private final SelectExpression expressionSelector;

    public SimpleAdapterFactory() {
        this(new SelectExpression());
    }
    public SimpleAdapterFactory(SelectExpression expressionSelector) {
        this.expressionSelector = expressionSelector;
    }

    @Override
    public DocumentAdapter newDocumentAdapter(Document doc) {
        return newDocumentAdapter(doc, false);
    }

    public DocumentAdapter newDocumentAdapter(Document doc, boolean isUpdate) {
        if (isUpdate) {
            return new SimpleDocumentAdapter(doc);
        }
        return new SimpleDocumentAdapter(doc, doc);
    }

    @Override
    public List<UpdateAdapter> newUpdateAdapterList(DocumentUpdate upd) {
        List<UpdateAdapter> ret = new ArrayList<>();
        DocumentType docType = upd.getDocumentType();
        DocumentId docId = upd.getId();
        Document complete = new Document(docType, upd.getId());
        for (FieldPathUpdate fieldUpd : upd) {
            if (FieldPathUpdateHelper.isComplete(fieldUpd)) {
                // A 'complete' field path update is basically a regular top-level field update
                // in wolf's clothing. Convert it to a regular field update to be friendlier
                // towards the search core backend.
                FieldPathUpdateHelper.applyUpdate(fieldUpd, complete);
            } else {
                ret.add(new IdentityFieldPathUpdateAdapter(fieldUpd, newDocumentAdapter(complete, true)));
            }
        }
        for (FieldUpdate fieldUpd : upd.getFieldUpdates()) {
            Field field = fieldUpd.getField();
            for (ValueUpdate valueUpd : fieldUpd.getValueUpdates()) {
                if (FieldUpdateHelper.isComplete(field, valueUpd)) {
                    FieldUpdateHelper.applyUpdate(field, valueUpd, complete);
                } else {
                    Document partial = FieldUpdateHelper.newPartialDocument(docType, docId, field, valueUpd);
                    ret.add(FieldUpdateAdapter.fromPartialUpdate(expressionSelector.selectExpression(docType, field.getName()),newDocumentAdapter(partial, true), valueUpd));
                }
            }
        }
        ret.add(FieldUpdateAdapter.fromCompleteUpdate(newDocumentAdapter(complete, true)));
        return ret;
    }
}
