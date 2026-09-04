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
 * Adapter for running an element assign, e.g. <code>"my_array[3]": {"assign": "foo"}</code>,
 * through the indexing script, so that the assigned value gets the same processing (e.g.
 * linguistics annotations) as it would get in a put. Without this the raw value reaches the
 * backend unprocessed, and e.g. an assigned element of an indexed string array produces no
 * index tokens.
 *
 * <p>The element cannot be applied to an empty document, since assigning to an array index
 * beyond the array size is a no-op. Instead the element is wrapped as a single-element array
 * in the target field of a partial document (see {@link FieldPathUpdateHelper#newElementAssignPartialDocument}),
 * the script processes that document, and the processed element is unwrapped into a new assign
 * to the original element index.</p>
 *
 * <p>Only the field targeted by the update is reconstructed. The script may also write derived
 * fields from the same input, but nothing guarantees those are element-wise aligned with the
 * target field, so no element assign is emitted for them.</p>
 *
 * @author johsol
 */
public class ElementAssignFieldPathUpdateFieldValues implements UpdateFieldValues {

    private static final Logger log = Logger.getLogger(ElementAssignFieldPathUpdateFieldValues.class.getName());

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

    @Override
    public DocumentUpdate getOutput() {
        Document doc = adapter.getFullOutput();
        DocumentUpdate out = new DocumentUpdate(doc.getDataType(), doc.getId());
        FieldValue processed = doc.getFieldValue(targetField());
        if (processed instanceof Array<?> array && array.size() == 1) {
            String path = targetField().getName() + "[" + elementIndex() + "]";
            AssignFieldPathUpdate assign = new AssignFieldPathUpdate(update.getDocumentType(), path,
                                                                     array.getFieldValue(0));
            // Carry over the flags of the update as fed: the script processes the value only, and
            // e.g. removeIfZero decides whether an assigned zero removes the element instead.
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

    private Field targetField() {
        return update.getFieldPath().get(0).getFieldRef();
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

    @Override
    public String toString() {
        return "element assign field values for '" + update.getOriginalFieldPath() + "': " + adapter;
    }

}
