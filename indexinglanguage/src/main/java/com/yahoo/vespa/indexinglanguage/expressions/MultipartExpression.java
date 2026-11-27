// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines the values of its expressions into one multipart value, an array of parts: 'expression1, expression2'.
 * The array type is always {@code array<any>}, also when all parts have the same type.
 * An {@code array<string>} has another meaning to embed: each element is a separate value.
 *
 * @author glebashnik
 */
public final class MultipartExpression extends ExpressionList<Expression> {

    private static final ArrayDataType valueType = new ArrayDataType(AnyDataType.instance);

    public MultipartExpression(Collection<? extends Expression> expressions) {
        super(expressions);
    }

    static boolean isMultipartType(DataType type) {
        return type instanceof ArrayDataType array && array.getNestedType() instanceof AnyDataType;
    }

    @Override
    public boolean requiresInput() {
        return expressions().stream().anyMatch(expression -> expression.requiresInput());
    }

    @Override
    public MultipartExpression convertChildren(ExpressionConverter converter) {
        return new MultipartExpression(convertChildList(converter));
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        List<DataType> partTypes = new ArrayList<>(expressions().size());
        for (var expression : expressions())
            partTypes.add(expression.setInputType(inputType, context));
        var multipartType = new MultipartDataType(partTypes);
        super.setOutputType(multipartType, context);
        return multipartType;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        if (outputType != null && ! (outputType instanceof MultipartDataType))
            throw new VerificationException(this, "Required to produce " + outputType.getName() +
                                                  ", but this produces a multipart input");
        var multipart = (MultipartDataType)outputType;
        if (multipart != null && multipart.partTypes().size() != expressions().size())
            throw new VerificationException(this, "This has " + expressions().size() + " parts, " +
                                                  "but the required number of parts is " + multipart.partTypes().size());
        super.setOutputType(outputType, context);
        DataType inputType = AnyDataType.instance;
        for (int i = 0; i < expressions().size(); i++) {
            DataType partType = multipart == null ? null : multipart.partTypes().get(i);
            inputType = leastGeneralOf(inputType, expressions().get(i).setOutputType(partType, context));
        }
        return inputType;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        Array parts = valueType.createFieldValue();
        for (Expression expression : this) {
            FieldValue part = context.setCurrentValue(input).execute(expression).getCurrentValue();
            if (part != null) {
                parts.add(part);
            }
            else if ( ! context.isComplete()) {
                // If a partial update does not include all parts of the multipart input, the embedded value is unchanged
                context.setCurrentValue(null);
                return;
            }
        }
        context.setCurrentValue(parts.isEmpty() ? null : parts);
    }

    @Override
    public String toString() {
        return expressions().stream().map(Expression::toString).collect(Collectors.joining(", "));
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof MultipartExpression;
    }

}
