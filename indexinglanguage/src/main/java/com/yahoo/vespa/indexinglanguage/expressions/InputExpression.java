// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.FieldPath;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Simon Thoresen Hult
 */
public class InputExpression extends Expression {

    private final String fieldName;
    private FieldPath fieldPath;

    public InputExpression(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    protected void doExecute(ExecutionContext ctx)
    {
        if (fieldPath != null) {
            ctx.setValue(ctx.getInputValue(fieldPath));
        } else {
            ctx.setValue(ctx.getInputValue(fieldName));
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType val = context.getInputType(this, fieldName);
        if (val == null) {
            throw new VerificationException(this, "Field '" + fieldName + "' not found.");
        }
        context.setValue(val);
    }

    @Override
    public DataType requiredInputType() {
        return null;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "input" + (fieldName != null ? " " + fieldName : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof InputExpression)) {
            return false;
        }
        InputExpression rhs = (InputExpression)obj;
        if (!equals(fieldName, rhs.fieldName)) {
            return false;
        }
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
        private List<String> inputFieldNames = new ArrayList<>(1);

        public List<String> getInputFieldNames() { return inputFieldNames; }

        @Override
        public void execute(Object obj) {
            inputFieldNames.add(((InputExpression) obj).getFieldName());
        }

        @Override
        public boolean check(Object obj) {
            return obj instanceof InputExpression;
        }
    }
}
