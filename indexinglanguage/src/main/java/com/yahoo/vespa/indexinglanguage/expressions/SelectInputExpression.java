// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.Pair;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Arrays;
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
        this(Arrays.asList(cases));
    }

    public SelectInputExpression(List<Pair<String, Expression>> cases) {
        super(null);
        this.cases = cases;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        for (var casePair : cases)
            casePair.getSecond().setStatementOutput(documentType, field);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        for (Pair<String, Expression> entry : cases) {
            FieldValue val = context.getInputValue(entry.getFirst());
            if (val != null) {
                context.setValue(val).execute(entry.getSecond());
                break;
            }
        }
        context.setValue(input);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        for (Pair<String, Expression> entry : cases) {
            DataType val = context.getInputType(this, entry.getFirst());
            if (val == null) {
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found.");
            }
            context.setValueType(val).execute(entry.getSecond());
        }
        context.setValueType(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        for (Pair<String, Expression> entry : cases) {
            select(entry.getSecond(), predicate, operation);
        }
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

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
