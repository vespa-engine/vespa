// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("rawtypes")
public class FieldValuesFactory {

    public static class SelectExpression {
        public Expression selectExpression(DocumentType documentType, String fieldName) {
            return null;
        }
    }

    private final SelectExpression expressionSelector;

    public FieldValuesFactory() {
        this(new SelectExpression());
    }

    public FieldValuesFactory(SelectExpression expressionSelector) {
        this.expressionSelector = expressionSelector;
    }

    public DocumentFieldValues asFieldValues(Document doc) {
        return newDocumentAdapter(doc, false);
    }

    public static DocumentFieldValues newDocumentAdapter(Document doc, boolean isUpdate) {
        return isUpdate ? new SimpleDocumentFieldValues(doc) : new SimpleDocumentFieldValues(doc, doc);
    }

    public List<UpdateFieldValues> asFieldValues(DocumentUpdate update) {
        List<UpdateFieldValues> ret = new ArrayList<>();
        DocumentType docType = update.getDocumentType();
        DocumentId docId = update.getId();
        Document complete = new Document(docType, update.getId());
        for (FieldPathUpdate fieldUpdate : update) {
            try {
                if (FieldPathUpdateHelper.isFieldValues(fieldUpdate)) {
                    // A 'complete' field path update is basically a regular top-level field update
                    // in wolf's clothing. Convert it to a regular field update to be friendlier
                    // towards the search core backend.
                    FieldPathUpdateHelper.applyUpdate(fieldUpdate, complete);
                } else {
                    ret.add(new IdentityFieldPathUpdateFieldValues(fieldUpdate, newDocumentAdapter(complete, true)));
                }
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("Exception during handling of update '" + fieldUpdate +
                                                   "' to field '" + fieldUpdate.getFieldPath() + "'", e);
            }
        }
        for (FieldUpdate fieldUpdate : update.fieldUpdates()) {
            Field field = fieldUpdate.getField();
            for (ValueUpdate valueUpdate : fieldUpdate.getValueUpdates()) {
                try {
                    if (FieldUpdateHelper.isFieldValues(field, valueUpdate)) {
                        FieldUpdateHelper.applyUpdate(field, valueUpdate, complete);
                    } else {
                        Document partial = FieldUpdateHelper.newPartialDocument(docType, docId, field, valueUpdate);
                        ret.add(FieldUpdateFieldValues.fromPartialUpdate(expressionSelector.selectExpression(docType, field.getName()),
                                                                         newDocumentAdapter(partial, true),
                                                                         valueUpdate));
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Could not execute update '" + valueUpdate + "' to " + field, e);
                }
            }
        }
        ret.add(FieldUpdateFieldValues.fromCompleteUpdate(newDocumentAdapter(complete, true)));
        return ret;
    }

}
