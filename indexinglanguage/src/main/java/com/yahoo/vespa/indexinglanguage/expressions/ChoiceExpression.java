// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An expression which returns the value of the first of a list of subexpressions which
 * returns a non-null value.
 *
 * Syntax: sub-expression1 || sub-expression2 || ...
 *
 * @author bratseth
 */
public class ChoiceExpression extends ExpressionList<Expression> {

    public ChoiceExpression() {
        this(List.of());
    }

    public ChoiceExpression(Expression... choices) {
        this(List.of(choices));
    }

    public ChoiceExpression(Collection<? extends Expression> choices) {
        super(choices, resolveInputType(choices));
    }

    @Override
    public ChoiceExpression convertChildren(ExpressionConverter converter) {
        return new ChoiceExpression(asList().stream().map(choice -> converter.branch().convert(choice)).toList());
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);

        DataType resolvedType = null;
        boolean resolvedTypeNeverAssigned = true;
        for (var expression : expressions()) {
            DataType outputType = expression.setInputType(inputType, context);
            resolvedType = resolvedTypeNeverAssigned ? outputType : mostGeneralOf(resolvedType, outputType);
            resolvedTypeNeverAssigned = false;
        }
        return resolvedType != null ? resolvedType : getOutputType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);

        DataType resolvedType = null;
        boolean resolvedTypeNeverAssigned = true;
        for (var expression : expressions()) {
            DataType inputType = expression.setOutputType(outputType, context);
            resolvedType = resolvedTypeNeverAssigned ? inputType : mostGeneralOf(resolvedType, inputType);
            resolvedTypeNeverAssigned = false;
        }
        return resolvedType != null ? resolvedType : getInputType(context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        context.setCurrentType(input);
        for (Expression exp : this)
            context.setCurrentType(input).verify(exp);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        for (Expression expression : this) {
            context.setCurrentValue(input).execute(expression);
            if (context.getCurrentValue() != null)
                break; // value found
        }
    }

    private static DataType resolveInputType(Collection<? extends Expression> list) {
        DataType previousInput = null;
        DataType previousOutput = null;
        for (Expression choice : list) {
            DataType thisInput = choice.requiredInputType();
            if (previousInput == null)
                previousInput = thisInput;
            else if (thisInput != null && !previousInput.isAssignableFrom(thisInput))
                throw new VerificationException(ScriptExpression.class, "Choice expression require conflicting input types, " +
                                                                        previousInput.getName() + " vs " + thisInput.getName());

            DataType thisOutput = choice.createdOutputType();
            if (previousOutput == null)
                previousOutput = thisOutput;
            else if (thisOutput != null && !previousOutput.isAssignableFrom(thisOutput))
                throw new VerificationException(ScriptExpression.class, "Choice expression produce conflicting output types, " +
                                                                        previousOutput.getName() + " vs " + thisOutput.getName());
        }
        return previousInput;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return asList().stream().map(Object::toString).collect(Collectors.joining(" || "));
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof ChoiceExpression;
    }

}
