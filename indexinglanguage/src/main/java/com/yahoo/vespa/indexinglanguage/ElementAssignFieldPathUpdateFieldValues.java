// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValues;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapts a field path update with assign method to execute an indexing script on it.
 *
 * Transforms field path update { "assign": { "arr[index]": value } } to a partial document
 * with an array of size 1. The index is temporarily set to 0 to perform the indexing script
 * on the partial document. This is necessary because assigning to an index out of bounds, in
 * this case over 0, would be no-op for field path updates. The getOutput then reassembles the
 * new assign field path update.
 *
 * @author johsol
 */
public class ElementAssignFieldPathUpdateFieldValues implements UpdateFieldValues {

    private static final Logger log = Logger.getLogger(ElementAssignFieldPathUpdateFieldValues.class.getName());

    /** The document adapter the indexing script will act on */
    private final DocumentFieldValues adapter;
    private final Expression optimizedExpression;

    /** The field path update we are processing. */
    private final AssignFieldPathUpdate update;

    public ElementAssignFieldPathUpdateFieldValues(Expression optimizedExpression,
                                                   DocumentFieldValues adapter,
                                                   AssignFieldPathUpdate update) {
        this.adapter = adapter;
        this.optimizedExpression = optimizedExpression;
        this.update = update;
    }

    /**
     * Builds a document update with the processed value.
     *
     * The partial document has now been processed, and we can create the document update
     * with the original field path and the newly calculated value.
     */
    @Override
    public DocumentUpdate getOutput() {
        Document doc = adapter.getFullOutput();
        DocumentUpdate out = new DocumentUpdate(doc.getDataType(), doc.getId());
        FieldValue processed = doc.getFieldValue(targetField());
        if (processed instanceof Array<?> array && array.size() == 1) {
            // Cloning the field path update, restoring the original element index
            String path = targetField().getName() + "[" + elementIndex() + "]";
            var newValue = array.getFieldValue(0);
            var assign = new AssignFieldPathUpdate(update.getDocumentType(), path, newValue);
            assign.setCreateMissingPath(update.getCreateMissingPath());
            assign.setRemoveIfZero(update.getRemoveIfZero());
            out.addFieldPathUpdate(assign);
        } else {
            // The script did not output a single processed element for the target field, e.g. because
            // it writes the input to other fields only. Pass the update on as fed, but be loud about
            // it: this is the unprocessed value reaching the backend which this class exists to prevent.
            log.log(Level.WARNING, () -> "Indexing script for field '" + targetField().getName() + "' of " +
                                         doc.getId() + " did not produce a processed element for '" +
                                         update.getOriginalFieldPath() + "', got " + processed +
                                         ". Passing the element assign on unprocessed.");
            out.addFieldPathUpdate(update);
        }
        return out;
    }

    /** The field that the original document update was targeting. */
    private Field targetField() {
        return update.getFieldPath().get(0).getFieldRef();
    }

    /** The index that the original field path update was targeting. */
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

    @Override
    public String toString() {
        return "element assign field values for '" + update.getOriginalFieldPath() + "': " + adapter;
    }

}
