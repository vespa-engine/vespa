// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.FieldValueConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public final class ForEachExpression extends CompositeExpression {

    private final Expression exp;

    public ForEachExpression(Expression exp) {
        super(UnresolvedDataType.INSTANCE);
        this.exp = Objects.requireNonNull(exp);
    }

    public Expression getInnerExpression() {
        return exp;
    }

    @Override
    public ForEachExpression convertChildren(ExpressionConverter converter) {
        Expression converted = converter.convert(exp);
        return converted != null ?  new ForEachExpression(converted) : null;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        exp.setStatementOutput(documentType, field);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if (input instanceof Array || input instanceof WeightedSet) {
            FieldValue next = new MyConverter(context, exp).convert(input);
            if (next == null) {
                VerificationContext verificationContext = new VerificationContext(context.getFieldValue());
                context.fillVariableTypes(verificationContext);
                verificationContext.setCurrentType(input.getDataType()).verify(this);
                next = verificationContext.getCurrentType().createFieldValue();
            }
            context.setCurrentValue(next);
        } else if (input instanceof Struct) {
            context.setCurrentValue(new MyConverter(context, exp).convert(input));
        } else {
            throw new IllegalArgumentException("Expected Array, Struct or WeightedSet input, got " +
                                               input.getDataType().getName());
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType valueType = context.getCurrentType();
        if (valueType instanceof ArrayDataType || valueType instanceof WeightedSetDataType) {
            // Set type for block evaluation
            context.setCurrentType(((CollectionDataType)valueType).getNestedType());

            // Evaluate block, which sets value>Type to the output of the block
            context.verify(exp);

            // Value type outside block becomes the collection type having the block output type as argument
            if (valueType instanceof ArrayDataType) {
                context.setCurrentType(DataType.getArray(context.getCurrentType()));
            } else {
                WeightedSetDataType wset = (WeightedSetDataType)valueType;
                context.setCurrentType(DataType.getWeightedSet(context.getCurrentType(), wset.createIfNonExistent(), wset.removeIfZero()));
            }
        }
        else if (valueType instanceof StructDataType) {
            for (Field field : ((StructDataType)valueType).getFields()) {
                DataType fieldType = field.getDataType();
                DataType structValueType = context.setCurrentType(fieldType).verify(exp).getCurrentType();
                if (!fieldType.isAssignableFrom(structValueType))
                    throw new VerificationException(this, "Expected " + fieldType.getName() + " output, got " +
                                                          structValueType.getName());
            }
            context.setCurrentType(valueType);
        }
        else {
            throw new VerificationException(this, "Expected Array, Struct or WeightedSet input, got " +
                                                  valueType.getName());
        }
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
        if (!(obj instanceof ForEachExpression rhs)) return false;
        if (!exp.equals(rhs.exp)) return false;
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
            context.setCurrentValue(value).execute(expression);
            return context.getCurrentValue();
        }
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(exp, predicate, operation);
    }

}
