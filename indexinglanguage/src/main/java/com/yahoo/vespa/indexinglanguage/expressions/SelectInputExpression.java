// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.Pair;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class SelectInputExpression extends CompositeExpression {

    private final List<Pair<String, Expression>> cases;

    @SafeVarargs
    public SelectInputExpression(Pair<String, Expression>... cases) {
        this(Arrays.asList(cases));
    }

    public SelectInputExpression(List<Pair<String, Expression>> cases) {
        this.cases = cases;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        for (Pair<String, Expression> entry : cases) {
            FieldValue val = ctx.getInputValue(entry.getFirst());
            if (val != null) {
                ctx.setValue(val).execute(entry.getSecond());
                break;
            }
        }
        ctx.setValue(input);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValue();
        for (Pair<String, Expression> entry : cases) {
            DataType val = context.getInputType(this, entry.getFirst());
            if (val == null) {
                throw new VerificationException(this, "Field '" + entry.getFirst() + "' not found.");
            }
            context.setValue(val).execute(entry.getSecond());
        }
        context.setValue(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        for (Pair<String, Expression> entry : cases) {
            select(entry.getSecond(), predicate, operation);
        }
    }

    @Override
    public DataType requiredInputType() {
        return null;
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
        if (!(obj instanceof SelectInputExpression)) {
            return false;
        }
        SelectInputExpression rhs = (SelectInputExpression)obj;
        if (!cases.equals(rhs.cases)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + cases.hashCode();
    }
}
