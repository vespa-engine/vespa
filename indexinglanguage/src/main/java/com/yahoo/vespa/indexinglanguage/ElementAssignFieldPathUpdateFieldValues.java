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
import com.yahoo.vespa.indexinglanguage.expressions.InvalidInputException;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;


/**
 * Adapts a field path update with assign method to execute an indexing script on it.
 *
 * Transforms field path update { "assign": { "arr[index]": value } } to a partial document
 * holding the value as the only element of the array, at index 0, and runs the indexing script
 * on that. The update itself cannot be applied to build the partial document, as assigning to an
 * index beyond the array size is a no-op for field path updates. The getOutput then reassembles
 * an assign of the processed element to the original field path.
 *
 * This assumes the statements outputting to the target field process each element independently
 * of the others, e.g. <code>for_each { ... }</code>, <code>index</code>, <code>attribute</code>,
 * <code>summary</code> or <code>passthrough</code>, as the statements generated from a schema do.
 * The element is then processed the same way as it would be in a put of the whole array. A statement
 * whose output for an element depends on the rest of the array, its size or the element's position
 * sees only a one-element array here and would silently produce a different value than in a put.
 * If the statements do not output a single element, the update is rejected as invalid input.
 *
 * @author johsol
 */
public class ElementAssignFieldPathUpdateFieldValues implements UpdateFieldValues {

    /** The document adapter the indexing script will act on */
    private final DocumentFieldValues adapter;
    private final Expression optimizedExpression;

    /** The field path update we are processing. */
    private final AssignFieldPathUpdate update;

    /** Whether no statement outputs to the target field, so there is nothing to process the element with. */
    private boolean noStatements = false;

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
        if (noStatements) {
            // The field is only input to other fields, so the element is passed on as fed, as when
            // FieldValuesFactory finds no statements outputting to it.
            out.addFieldPathUpdate(update);
        } else if (processed instanceof Array<?> array && array.size() == 1) {
            // Element-wise processing (see class comment) maps the single input element to this one
            var assign = new AssignFieldPathUpdate(update.getDocumentType(), update.getOriginalFieldPath(),
                                                   array.getFieldValue(0));
            assign.setCreateMissingPath(update.getCreateMissingPath());
            assign.setRemoveIfZero(update.getRemoveIfZero());
            out.addFieldPathUpdate(assign);
        } else {
            // The script did not output a single processed element for the target field, so there is no
            // element to assign. Passing the update on as fed would let the unprocessed value reach the
            // backend, which this class exists to prevent.
            throw new InvalidInputException("Cannot assign to '" + update.getOriginalFieldPath() + "': " +
                                            "The indexing statements of '" + targetField().getName() +
                                            "' must output a single element for an element assign, but got " +
                                            describe(processed));
        }
        return out;
    }

    private static String describe(FieldValue value) {
        if (value == null) return "nothing";
        if (value instanceof Array<?> array) return "an array of " + array.size() + " elements";
        return "a " + value.getDataType().getName();
    }

    /** The field that the original document update was targeting. */
    private Field targetField() {
        return update.getFieldPath().get(0).getFieldRef();
    }

    @Override
    public Expression getExpression(Expression expression) {
        if (optimizedExpression != null) return optimizedExpression;
        Expression statements = statementsOutputting(expression, targetField().getName());
        noStatements = (statements == null);
        return statements != null ? statements : new ScriptExpression();
    }

    /**
     * Returns the statements of the given expression which output to the given field, or null if there are none.
     *
     * Only the assigned element of the target field is emitted, so statements writing other (derived) fields
     * would run for nothing. Some, like embed or generate, are expensive and may fail the whole update.
     */
    public static Expression statementsOutputting(Expression expression, String fieldName) {
        if (expression instanceof ScriptExpression script) {
            var statements = script.asList().stream().filter(statement -> outputsTo(statement, fieldName)).toList();
            return statements.isEmpty() ? null : new ScriptExpression(statements);
        }
        return outputsTo(expression, fieldName) ? expression : null;
    }

    private static boolean outputsTo(Expression expression, String fieldName) {
        var extractor = new OutputExpression.OutputFieldNameExtractor();
        expression.select(extractor, extractor);
        return extractor.getOutputFieldNames().contains(fieldName);
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
