// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValues;

import java.util.Iterator;
import java.util.Map;

/**
 * Adapter for running an element assign, e.g. <code>"my_array[3]": {"assign": "foo"}</code>,
 * through the indexing script, so that the assigned value gets the same processing (e.g.
 * linguistics annotations) as it would get in a put. Without this the raw value reaches the
 * backend unprocessed, and e.g. an assigned element of an indexed string array produces no
 * index tokens.
 *
 * <p>The element cannot be applied to an empty document, since assigning to an array index
 * beyond the array size is a no-op. Instead the element is wrapped as a single-element array
 * in the target field of a partial document, the script processes that document, and the
 * processed element is unwrapped into a new assign to the original element index.</p>
 *
 * @author johsol
 */
public class ElementAssignFieldPathUpdateFieldValues implements UpdateFieldValues {

    private final DocumentFieldValues adapter;
    private final Expression optimizedExpression;
    private final AssignFieldPathUpdate update;

    public ElementAssignFieldPathUpdateFieldValues(Expression optimizedExpression,
                                                   DocumentFieldValues adapter,
                                                   AssignFieldPathUpdate update) {
        this.adapter = adapter;
        this.optimizedExpression = optimizedExpression;
        this.update = update;
    }

    /** Creates a partial document holding the assigned element as a single-element array in the target field. */
    @SuppressWarnings("unchecked")
    public static Document newPartialDocument(DocumentType docType, DocumentId docId, AssignFieldPathUpdate update) {
        Document doc = new Document(docType, docId);
        Field field = update.getFieldPath().get(0).getFieldRef();
        Array<FieldValue> array = (Array<FieldValue>)field.getDataType().createFieldValue();
        array.add(update.getNewValue().clone());
        doc.setFieldValue(field, array);
        return doc;
    }

    @Override
    public DocumentUpdate getOutput() {
        Document doc = adapter.getFullOutput();
        DocumentUpdate out = new DocumentUpdate(doc.getDataType(), doc.getId());
        for (Iterator<Map.Entry<Field, FieldValue>> it = doc.iterator(); it.hasNext();) {
            Map.Entry<Field, FieldValue> entry = it.next();
            if (!(entry.getValue() instanceof Array<?> array) || array.size() != 1) {
                // An element assign can only be reconstructed from a single processed element.
                continue;
            }
            String path = entry.getKey().getName() + "[" + elementIndex() + "]";
            out.addFieldPathUpdate(new AssignFieldPathUpdate(update.getDocumentType(), path,
                                                             update.getOriginalWhereClause(),
                                                             array.getFieldValue(0)));
        }
        if (out.fieldPathUpdates().isEmpty()) {
            // The script produced no usable output for this field; keep the update as fed.
            out.addFieldPathUpdate(update);
        }
        return out;
    }

    private int elementIndex() {
        return update.getFieldPath().get(1).getLookupIndex();
    }

    @Override
    public Expression getExpression(Expression expression) {
        return optimizedExpression != null ? optimizedExpression : expression;
    }

    @Override
    public DataType getFieldType(String fieldName, Expression exp) {
        return adapter.getFieldType(fieldName, exp);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return adapter.getInputValue(fieldName);
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        return adapter.getInputValue(fieldPath);
    }

    @Override
    public FieldValues setOutputValue(String fieldName, FieldValue fieldValue, Expression exp) {
        return adapter.setOutputValue(fieldName, fieldValue, exp);
    }

}
