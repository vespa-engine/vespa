// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
// TODO: Support Map in addition to Array and Weghted Set (doc just says "collection type")
public final class CatExpression extends ExpressionList<Expression> {

    public CatExpression(Expression... expressions) {
        this(List.of(expressions));
    }

    public CatExpression(Collection<? extends Expression> expressions) {
        super(expressions, resolveInputType(expressions));
    }

    @Override
    public boolean requiresInput() { return false; }

    @Override
    public CatExpression convertChildren(ExpressionConverter converter) {
        return new CatExpression(convertChildList(converter));
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);

        List<DataType> outputTypes = new ArrayList<>(expressions().size());
        for (var expression : expressions())
            outputTypes.add(expression.setInputType(inputType, context));
        DataType outputType = resolveOutputType(outputTypes);
        if (outputType == null) outputType = getOutputType(context); // TODO: Remove this line
        super.setOutputType(outputType, context);
        return outputType;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        if (outputType != DataType.STRING && ! (outputType instanceof CollectionDataType))
            throw new VerificationException(this, "Required to produce " + outputType.getName() +
                                                  ", but this produces a string or collection");
        super.setOutputType(outputType, context);
        for (var expression : expressions())
            expression.setOutputType(AnyDataType.instance, context); // Any output is handled by converting to string

        if (outputType instanceof CollectionDataType)
            return outputType;
        else
            return getInputType(context); // Cannot infer input type since we take the string value
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        List<DataType> types = new LinkedList<>();
        for (Expression expression : this) {
            DataType type = context.setCurrentType(input).verify(expression).getCurrentType();
            if (type == null)
                throw new VerificationException(this, "In " + expression + ": Attempting to concatenate a null value");
            types.add(type);
        }
        context.setCurrentType(resolveOutputType(types));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        DataType inputType = input != null ? input.getDataType() : null;
//        VerificationContext verificationContext = new VerificationContext(context.getFieldValue());
//        context.fillVariableTypes(verificationContext);
        List<FieldValue> values = new LinkedList<>();
//        List<DataType> types = new LinkedList<>();
        for (Expression expression : this) {
            FieldValue val = context.setCurrentValue(input).execute(expression).getCurrentValue();
            values.add(val);
/*
            DataType type;
            if (val != null) {
                type = val.getDataType();
            } else {
                type = verificationContext.setCurrentType(inputType).verify(this).getCurrentType();
            }
            types.add(type);

 */
        }
//        DataType type = resolveOutputType(types);
        DataType type = getOutputType();
        if (type == null)
            throw new RuntimeException("Output type is not resolved in " + this);
        context.setCurrentValue(type == DataType.STRING ? asString(values) : asCollection(type, values));
    }

    private static DataType resolveInputType(Collection<? extends Expression> list) {
        DataType prev = null;
        for (Expression exp : list) {
            DataType next = exp.requiredInputType();
            if (next == null) {
                // ignore
            } else if (prev == null) {
                prev = next;
            } else if (!prev.isAssignableFrom(next)) {
                throw new VerificationException(CatExpression.class, "Operands require conflicting input types, " +
                                                                      prev.getName() + " vs " + next.getName());
            }
        }
        return prev;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (Iterator<Expression> it = iterator(); it.hasNext();) {
            ret.append(it.next());
            if (it.hasNext()) {
                ret.append(" . ");
            }
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof CatExpression;
    }

    /** We're either concatenating strings, or collections. */
    private static DataType resolveOutputType(List<DataType> types) {
        DataType resolved = null;
        for (DataType type : types) {
            if (type == null) return null;
            if (!(type instanceof CollectionDataType)) return DataType.STRING;

            if (resolved == null)
                resolved = type;
            else if (!resolved.isAssignableFrom(type))
                return DataType.STRING;
        }
        return resolved;
    }

    private static FieldValue asString(List<FieldValue> outputs) {
        StringBuilder ret = new StringBuilder();
        for (FieldValue val : outputs) {
            if (val == null) {
                return null;
            }
            ret.append(val);
        }
        return new StringFieldValue(ret.toString());
    }

    private static FieldValue asCollection(DataType type, List<FieldValue> values) {
        if (type instanceof ArrayDataType) {
            return asArray((ArrayDataType)type, values);
        } else if (type instanceof WeightedSetDataType) {
            return asWset((WeightedSetDataType)type, values);
        } else {
            throw new UnsupportedOperationException(type.getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static FieldValue asArray(ArrayDataType arrType, List<FieldValue> values) {
        Array out = arrType.createFieldValue();
        for (FieldValue val : values) {
            if (val == null) {
                continue;
            }
            out.addAll((Array)val);
        }
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static FieldValue asWset(WeightedSetDataType wsetType, List<FieldValue> values) {
        WeightedSet out = wsetType.createFieldValue();
        for (FieldValue val : values) {
            if (val == null) {
                continue;
            }
            out.putAll((WeightedSet)val);
        }
        return out;
    }

}
