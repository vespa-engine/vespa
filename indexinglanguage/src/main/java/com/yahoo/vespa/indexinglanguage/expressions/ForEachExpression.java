// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.indexinglanguage.FieldValueConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

/**
 * @author Simon Thoresen
 */
public class ForEachExpression extends CompositeExpression {

    private final Expression exp;

    public ForEachExpression(Expression exp) {
        this.exp = exp;
    }

    public Expression getInnerExpression() {
        return exp;
    }

    @Override
    protected void doExecute(final ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        if (input instanceof Array || input instanceof WeightedSet) {
            FieldValue next = new MyConverter(ctx, exp).convert(input);
            if (next == null) {
                VerificationContext vctx = new VerificationContext(ctx);
                vctx.setValue(input.getDataType()).execute(this);
                next = vctx.getValue().createFieldValue();
            }
            ctx.setValue(next);
        } else if (input instanceof Struct) {
            ctx.setValue(new MyConverter(ctx, exp).convert(input));
        } else {
            throw new IllegalArgumentException("Expected Array, Struct or WeightedSet input, got " +
                                               input.getDataType().getName() + ".");
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValue();
        if (input instanceof ArrayDataType || input instanceof WeightedSetDataType) {
            context.setValue(((CollectionDataType)input).getNestedType()).execute(exp);
            if (input instanceof ArrayDataType) {
                context.setValue(DataType.getArray(context.getValue()));
            } else {
                WeightedSetDataType wset = (WeightedSetDataType)input;
                context.setValue(DataType.getWeightedSet(context.getValue(), wset.createIfNonExistent(), wset.removeIfZero()));
            }
        } else if (input instanceof StructDataType) {
            for (Field field : ((StructDataType)input).getFields()) {
                DataType fieldType = field.getDataType();
                DataType valueType = context.setValue(fieldType).execute(exp).getValue();
                if (!fieldType.isAssignableFrom(valueType)) {
                    throw new VerificationException(this, "Expected " + fieldType.getName() + " output, got " +
                                                          valueType.getName() + ".");
                }
            }
            context.setValue(input);
        } else {
            throw new VerificationException(this, "Expected Array, Struct or WeightedSet input, got " +
                                                  input.getName() + ".");
        }
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        if (exp.createdOutputType() == null) {
            return null;
        }
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "for_each { " + exp + " }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ForEachExpression)) {
            return false;
        }
        ForEachExpression rhs = (ForEachExpression)obj;
        if (!exp.equals(rhs.exp)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + exp.hashCode();
    }

    private static final class MyConverter extends FieldValueConverter {

        final ExecutionContext context;
        final Expression expression;
        int depth = 0;

        MyConverter(ExecutionContext context, Expression expression) {
            this.context = context;
            this.expression = expression;
        }

        @Override
        protected boolean shouldConvert(FieldValue value) {
            return ++depth > 1;
        }

        @Override
        protected FieldValue doConvert(FieldValue value) {
            context.setValue(value).execute(expression);
            return context.getValue();
        }
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(exp, predicate, operation);
    }
}
