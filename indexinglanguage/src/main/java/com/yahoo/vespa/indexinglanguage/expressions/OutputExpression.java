// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public abstract class OutputExpression extends Expression {

    private final String image;
    private final String fieldName;

    public OutputExpression(String image, String fieldName) {
        this.image = image;
        this.fieldName = fieldName;
    }

    @Override
    public boolean isMutating() { return false; }

    public String getFieldName() { return fieldName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        return super.setInputType(inputType, context.getFieldType(fieldName, this), context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        return context.getFieldType(fieldName, this);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.tryOutputType(fieldName, context.getCurrentType(), this);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setFieldValue(fieldName, context.getCurrentValue(), this);
    }

    @Override
    public DataType createdOutputType() { return null; }

    @Override
    public DataType getInputType(VerificationContext context) {
        return context.getFieldType(fieldName, this);
    }

    @Override
    public String toString() {
        return image + (fieldName != null ? " " + fieldName : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OutputExpression rhs)) return false;
        if (!Objects.equals(fieldName, rhs.fieldName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + (fieldName != null ? fieldName.hashCode() : 0);
    }

    public static class OutputFieldNameExtractor implements ObjectOperation, ObjectPredicate {

        private final List<String> outputFieldNames = new ArrayList<>(1);

        public List<String> getOutputFieldNames() { return outputFieldNames; }

        @Override
        public void execute(Object obj) {
            outputFieldNames.add(((OutputExpression) obj).getFieldName());
        }

        @Override
        public boolean check(Object obj) {
            return obj instanceof OutputExpression;
        }

    }

}
