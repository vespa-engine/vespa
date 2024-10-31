// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.Pair;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Collections;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public final class SelectInputExpression extends CompositeExpression {

    private final List<Pair<String, Expression>> cases;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public SelectInputExpression(Pair<String, Expression>... cases) {
        this(List.of(cases));
    }

    public SelectInputExpression(List<Pair<String, Expression>> cases) {
        super(null);
        this.cases = cases;
    }

    @Override
    public SelectInputExpression convertChildren(ExpressionConverter converter) {
        return new SelectInputExpression(cases.stream()
                                              .map(c -> new Pair<>(c.getFirst(), converter.branch().convert(c.getSecond())))
                                              .toList());
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);

        DataType outputType = null;
        boolean outputNeverAssigned = true; // Needed to separate this null case from the "cannot be inferred" case
        for (Pair<String, Expression> entry : cases) {
            DataType fieldType = context.getFieldType(entry.getFirst(), this);
            if (fieldType == null)
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found");
            var entryOutputType = entry.getSecond().setInputType(fieldType, context);
            outputType = outputNeverAssigned ? entryOutputType : mostGeneralOf(outputType, entryOutputType);
            outputNeverAssigned = false;
        }
        return outputType;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);

        for (Pair<String, Expression> entry : cases) {
            DataType fieldType = context.getFieldType(entry.getFirst(), this);
            if (fieldType == null)
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found");
            DataType inputType = entry.getSecond().setOutputType(outputType, context);
            if ( ! fieldType.isAssignableTo(inputType))
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found");
        }
        return AnyDataType.instance;
    }

    private DataType mostGeneralOf(DataType left, DataType right) {
        if (left == null || right == null) return right;
        return left.isAssignableTo(right) ? right : left;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        for (var casePair : cases)
            casePair.getSecond().setStatementOutput(documentType, field);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        for (Pair<String, Expression> entry : cases) {
            DataType val = context.getFieldType(entry.getFirst(), this);
            if (val == null) {
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found");
            }
            context.setCurrentType(val).verify(entry.getSecond());
        }
        context.setCurrentType(input);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        for (Pair<String, Expression> entry : cases) {
            FieldValue val = context.getFieldValue(entry.getFirst());
            if (val != null) {
                context.setCurrentValue(val).execute(entry.getSecond());
                break;
            }
        }
        context.setCurrentValue(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        for (Pair<String, Expression> entry : cases) {
            select(entry.getSecond(), predicate, operation);
        }
    }

    @Override
    public DataType createdOutputType() { return null; }

    public List<Pair<String, Expression>> getCases() {
        return Collections.unmodifiableList(cases);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("select_input { ");
        for (Pair<String, Expression> entry : cases) {
            ret.append(entry.getFirst()).append(": ");
            Expression exp = entry.getSecond();
            ret.append(exp).append("; ");
        }
        ret.append("}");
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SelectInputExpression rhs)) return false;
        if (!cases.equals(rhs.cases)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + cases.hashCode();
    }

}
