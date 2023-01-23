// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.AdapterFactory;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public class DocumentScript {

    private final String documentType;
    private final Set<String> inputFields;
    private final Expression expression;

    public DocumentScript(String documentType, Collection<String> inputFields, Expression expression) {
        this.documentType = documentType;
        this.inputFields = new HashSet<>(inputFields);
        this.expression = expression;
    }

    public Expression getExpression() { return expression; }

    public Document execute(AdapterFactory adapterFactory, Document document) {
        for (var i = document.iterator(); i.hasNext(); ) {
            Map.Entry<Field, FieldValue> entry = i.next();
            requireThatFieldIsDeclaredInDocument(entry.getKey());
            removeAnyLinguisticsSpanTree(entry.getValue());
        }
        return expression.execute(adapterFactory, document);
    }

    public DocumentUpdate execute(AdapterFactory adapterFactory, DocumentUpdate update) {
        for (FieldUpdate fieldUpdate : update.fieldUpdates()) {
            requireThatFieldIsDeclaredInDocument(fieldUpdate.getField());
            for (ValueUpdate<?> valueUpdate : fieldUpdate.getValueUpdates()) {
                removeAnyLinguisticsSpanTree(valueUpdate);
            }
        }
        for (FieldPathUpdate fieldUpdate : update.fieldPathUpdates()) {
            requireThatFieldIsDeclaredInDocument(fieldUpdate.getFieldPath().get(0).getFieldRef());
            if (fieldUpdate instanceof AssignFieldPathUpdate) {
                removeAnyLinguisticsSpanTree(((AssignFieldPathUpdate)fieldUpdate).getFieldValue());
            }
        }
        return Expression.execute(expression, adapterFactory, update);
    }

    private void requireThatFieldIsDeclaredInDocument(Field field) {
        if (field != null && !inputFields.contains(field.getName())) {
            throw new IllegalArgumentException("Field '" + field.getName() + "' is not part of the declared document " +
                                               "type '" + documentType + "'.");
        }
    }

    private void removeAnyLinguisticsSpanTree(ValueUpdate<?> valueUpdate) {
        if (valueUpdate instanceof MapValueUpdate) {
            removeAnyLinguisticsSpanTree(((MapValueUpdate)valueUpdate).getUpdate());
        } else {
            removeAnyLinguisticsSpanTree(valueUpdate.getValue());
        }
    }

    private void removeAnyLinguisticsSpanTree(FieldValue value) {
        if (value instanceof StringFieldValue) {
            ((StringFieldValue)value).removeSpanTree(SpanTrees.LINGUISTICS);
        } else if (value instanceof Array<?> arr) {
            for (FieldValue fieldValue : arr.getValues()) {
                removeAnyLinguisticsSpanTree(fieldValue);
            }
        } else if (value instanceof WeightedSet<?> wset) {
            for (FieldValue fieldValue : wset.keySet()) {
                removeAnyLinguisticsSpanTree(fieldValue);
            }
        } else if (value instanceof MapFieldValue<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                removeAnyLinguisticsSpanTree((FieldValue)entry.getKey());
                removeAnyLinguisticsSpanTree((FieldValue)entry.getValue());
            }
        } else if (value instanceof StructuredFieldValue struct) {
            for (Iterator<Map.Entry<Field, FieldValue>> it = struct.iterator(); it.hasNext();) {
                removeAnyLinguisticsSpanTree(it.next().getValue());
            }
        }
    }

    @Override
    public String toString() {
        return "indexing script for '" + documentType + "' given inputs " + inputFields + ": " + expression;
    }

}
