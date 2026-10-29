// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
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
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.ExpressionSearcher;
import com.yahoo.vespa.indexinglanguage.FieldPathUpdateHelper;
import com.yahoo.vespa.indexinglanguage.FieldValuesFactory;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InvalidInputException;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Preprocesses field values (e.g. removing linguistic annotations), verifies that the
 * touching fields are declared ijputs of this script, and executes the {@link ScriptExpression}
 * on a {@link Document} or {@link DocumentUpdate}.
 *
 * @author Simon Thoresen Hult
 */
class DocumentScript {

    private final DocumentType documentType;
    private final Set<String> inputFields;
    private final ScriptExpression expression;
    private final Set<String> fastMapSearchFields;
    private final Set<String> structFieldIndexFields;

    DocumentScript(DocumentType documentType, Collection<String> inputFields, ScriptExpression expression,
                   Set<String> fastMapSearchFields) {
        this.documentType = documentType;
        this.inputFields = new HashSet<>(inputFields);
        this.expression = expression;
        this.fastMapSearchFields = Set.copyOf(fastMapSearchFields);
        this.structFieldIndexFields = structFieldIndexFields(expression);
        expression.resolve(documentType);
    }

    /**
     * Returns the input fields of the statements annotating struct fields in place (see {@link ForEachFieldExpression}),
     * which is how struct fields with 'indexing: index' are indexed.
     */
    private static Set<String> structFieldIndexFields(ScriptExpression script) {
        var fields = new HashSet<String>();
        for (StatementExpression statement : script.asList()) {
            if (new ExpressionSearcher<>(ForEachFieldExpression.class).containedIn(statement))
                fields.addAll(InputExpression.InputFieldNameExtractor.runOn(statement));
        }
        return Set.copyOf(fields);
    }

    ScriptExpression getExpression() { return expression; }

    Document execute(FieldValuesFactory fieldValuesFactory, Document document, boolean isReindexing, Instant deadline) {
        for (var i = document.iterator(); i.hasNext(); ) {
            Map.Entry<Field, FieldValue> entry = i.next();
            requireThatFieldIsDeclaredInDocument(entry.getKey());
            removeAnyLinguisticsSpanTree(entry.getValue());
        }
        return expression.execute(fieldValuesFactory, document, isReindexing, deadline);
    }

    DocumentUpdate execute(FieldValuesFactory fieldValuesFactory, DocumentUpdate update, Instant deadline) {
        for (FieldUpdate fieldUpdate : update.fieldUpdates()) {
            requireThatFieldIsDeclaredInDocument(fieldUpdate.getField());
            requireThatFieldUpdateIsSupported(fieldUpdate);
            for (ValueUpdate<?> valueUpdate : fieldUpdate.getValueUpdates()) {
                removeAnyLinguisticsSpanTree(valueUpdate);
            }
        }
        for (FieldPathUpdate fieldUpdate : update.fieldPathUpdates()) {
            Field field = fieldUpdate.getFieldPath().get(0).getFieldRef();
            requireThatFieldIsDeclaredInDocument(field);
            requireThatFieldPathUpdateIsSupported(field, fieldUpdate);
            if (fieldUpdate instanceof AssignFieldPathUpdate) {
                removeAnyLinguisticsSpanTree(((AssignFieldPathUpdate)fieldUpdate).getFieldValue());
            }
        }
        return Expression.execute(expression, fieldValuesFactory, update, deadline);
    }

    private void requireThatFieldIsDeclaredInDocument(Field field) {
        if (field != null && !inputFields.contains(field.getName()))
            throw new InvalidInputException("Field '" + field.getName() + "' is not part of the declared " + documentType);
    }

    /**
     * A map (or array of struct) with fast search is indexed into a synthetic key-value attribute derived from the
     * whole field, so a field path update reaching into it would leave that attribute holding only the updated entries.
     * Assigning the whole field is fine, as it is turned into a regular field update which reruns the script.
     */
    private void requireThatFieldPathUpdateIsSupported(Field field, FieldPathUpdate fieldUpdate) {
        if (field == null || FieldPathUpdateHelper.isFieldValues(fieldUpdate)) {
            return;
        }
        if (fastMapSearchFields.contains(field.getName())) {
            throw new InvalidInputException("Field '" + field.getName() + "' has 'fast-search-map-field', which does not " +
                                            "support field path updates into the field. Assign the whole field instead.");
        }
        // The updated values would not be annotated, so they would not be indexed
        if (structFieldIndexFields.contains(field.getName())) {
            throw new InvalidInputException("Field '" + field.getName() + "' has struct fields with 'indexing: index', " +
                                            "which does not support field path updates into the field. " +
                                            "Assign the whole field instead.");
        }
    }

    /**
     * An array of struct with fast search is indexed into a synthetic key-value attribute holding one entry per
     * element with both the key and the value set, so its elements do not line up with the array elements.
     * Updating a single element by index could then hit the wrong entry, and removing an element would also remove
     * the entry of any other element with the same key and value. Assigning, clearing and adding elements is fine.
     */
    private void requireThatFieldUpdateIsSupported(FieldUpdate fieldUpdate) {
        Field field = fieldUpdate.getField();
        if (field == null) {
            return;
        }
        if (structFieldIndexFields.contains(field.getName())) {
            requireNoElementUpdates(fieldUpdate);
        }
        if (!fastMapSearchFields.contains(field.getName())) {
            return;
        }
        if (!(field.getDataType() instanceof ArrayDataType)) {
            return;
        }
        for (ValueUpdate<?> valueUpdate : fieldUpdate.getValueUpdates()) {
            if (valueUpdate instanceof MapValueUpdate || valueUpdate instanceof RemoveValueUpdate) {
                throw new InvalidInputException("Field '" + field.getName() + "' has 'fast-search-map-field', which does " +
                                                "not support updating or removing single array elements. " +
                                                "Assign the whole field instead.");
            }
        }
    }

    /**
     * A field with struct field indexes has its indexed struct fields annotated by running the script on the whole
     * field, or on the elements added. Updating a single element (or map value) does not run the script on it,
     * so the updated element would not be indexed.
     */
    private static void requireNoElementUpdates(FieldUpdate fieldUpdate) {
        for (ValueUpdate<?> valueUpdate : fieldUpdate.getValueUpdates()) {
            if (valueUpdate instanceof MapValueUpdate) {
                throw new InvalidInputException("Field '" + fieldUpdate.getField().getName() + "' has struct fields with " +
                                                "'indexing: index', which does not support updating single elements. " +
                                                "Assign the whole field, or add or remove elements, instead.");
            }
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
