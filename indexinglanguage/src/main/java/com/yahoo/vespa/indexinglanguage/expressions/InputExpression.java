// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.FieldPath;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public final class InputExpression extends Expression {

    private final String fieldName;
    private FieldPath fieldPath;

    public InputExpression(String fieldName) {
        super(null);
        if (fieldName == null)
            throw new IllegalArgumentException("'input' must be given a field name as argument");
        this.fieldName = fieldName;
    }

    public String getFieldName() { return fieldName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return requireFieldType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(requireFieldType(context), outputType, null, context);
        return AnyDataType.instance;
    }

    private DataType requireFieldType(VerificationContext context) {
        DataType fieldType = context.getFieldType(fieldName, this);
        if (fieldType == null)
            throw new VerificationException(this, "Field '" + fieldName + "' not found");
        return fieldType;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(requireFieldType(context));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (fieldPath != null)
            context.setCurrentValue(context.getFieldValue(fieldPath));
        else
            context.setCurrentValue(context.getFieldValue(fieldName));
    }

    @Override
    public DataType createdOutputType() { return UnresolvedDataType.INSTANCE; }

    @Override
    public DataType getOutputType(VerificationContext context) {
        return context.getFieldType(fieldName, this);
    }

    @Override
    public String toString() {
        return "input" + (fieldName != null ? " " + fieldName : "");
    }

    @Override
    public boolean equals(Object obj) {
        if ( ! (obj instanceof InputExpression rhs)) return false;
        if ( ! equals(fieldName, rhs.fieldName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + (fieldName != null ? fieldName.hashCode() : 0);
    }

    public static class FieldPathOptimizer implements ObjectOperation, ObjectPredicate {

        private final DocumentType documentType;

        public FieldPathOptimizer(DocumentType documentType) {
            this.documentType = documentType;
        }

        @Override
        public void execute(Object obj) {
            InputExpression exp = (InputExpression) obj;
            exp.fieldPath = documentType.buildFieldPath(exp.getFieldName());
        }

        @Override
        public boolean check(Object obj) {
            return obj instanceof InputExpression;
        }

    }

    public static class InputFieldNameExtractor implements ObjectOperation, ObjectPredicate {

        private final List<String> inputFieldNames = new ArrayList<>(1);

        @Override
        public void execute(Object obj) {
            inputFieldNames.add(((InputExpression) obj).getFieldName());
        }

        @Override
        public boolean check(Object obj) {
            return obj instanceof InputExpression;
        }

        public static List<String> runOn(Expression expression) {
            InputExpression.InputFieldNameExtractor inputFieldNameExtractor = new InputExpression.InputFieldNameExtractor();
            expression.select(inputFieldNameExtractor, inputFieldNameExtractor);
            return inputFieldNameExtractor.inputFieldNames;
        }

    }

}
