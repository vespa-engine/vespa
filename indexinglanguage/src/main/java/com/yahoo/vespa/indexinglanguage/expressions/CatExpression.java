// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
public final class CatExpression extends ExpressionList<Expression> {

    public CatExpression(Expression... lst) {
        this(Arrays.asList(lst));
    }

    public CatExpression(Collection<? extends Expression> lst) {
        super(lst, resolveInputType(lst));
    }

    @Override
    public CatExpression convertChildren(ExpressionConverter converter) {
        return new CatExpression(convertChildList(converter));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        DataType inputType = input != null ? input.getDataType() : null;
        VerificationContext ver = new VerificationContext(context);
        List<FieldValue> values = new LinkedList<>();
        List<DataType> types = new LinkedList<>();
        for (Expression exp : this) {
            FieldValue val = context.setValue(input).execute(exp).getValue();
            values.add(val);

            DataType type;
            if (val != null) {
                type = val.getDataType();
            } else {
                type = ver.setValueType(inputType).execute(this).getValueType();
            }
            types.add(type);
        }
        DataType type = resolveOutputType(types);
        context.setValue(type == DataType.STRING ? asString(values) : asCollection(type, values));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        List<DataType> types = new LinkedList<>();
        for (Expression exp : this) {
            DataType val = context.setValueType(input).execute(exp).getValueType();
            types.add(val);
            if (val == null) {
                throw new VerificationException(this, "Attempting to concatenate a null value (" + exp + ").");
            }
        }
        context.setValueType(resolveOutputType(types));
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
                                                                      prev.getName() + " vs " + next.getName() + ".");
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

    private static DataType resolveOutputType(List<DataType> types) {
        DataType ret = null;
        for (DataType type : types) {
            if (!(type instanceof CollectionDataType)) {
                return DataType.STRING;
            }
            if (ret == null) {
                ret = type;
            } else if (!ret.isAssignableFrom(type)) {
                return DataType.STRING;
            }
        }
        return ret;
    }

    private static FieldValue asString(List<FieldValue> outputs) {
        StringBuilder ret = new StringBuilder();
        for (FieldValue val : outputs) {
            if (val == null) {
                return null;
            }
            ret.append(val.toString());
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
