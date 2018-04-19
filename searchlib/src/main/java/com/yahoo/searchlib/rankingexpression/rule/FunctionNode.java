// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.Join;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Invocation of a native function.
 *
 * @author simon
 * @author bratseth
 */
public final class FunctionNode extends CompositeNode {

    /** The type of function. */
    private final Function function;

    /** The arguments to this function. */
    private final Arguments arguments;

    /* Creates an unary function node */
    public FunctionNode(Function function, ExpressionNode argument) {
        if (function.arity() != 1) throw new IllegalArgumentException(function + " is not unary");
        this.function = function;
        this.arguments = new Arguments(Collections.singletonList(argument));
    }

    /** Creates a binary function node */
    public FunctionNode(Function function, ExpressionNode argument1, ExpressionNode argument2) {
        if (function.arity() != 2) throw new IllegalArgumentException(function + " is not binary");
        this.function = function;
        List<ExpressionNode> argumentList = new ArrayList<>();
        argumentList.add(argument1);
        argumentList.add(argument2);
        arguments=new Arguments(argumentList);
    }

    public Function getFunction() { return function; }

    /** Returns the arguments of this */
    @Override
    public List<ExpressionNode> children() {
        return arguments.expressions();
    }

    @Override
    public StringBuilder toString(StringBuilder b, SerializationContext context, Deque<String> path, CompositeNode parent) {
        b.append(function.toString()).append("(");
        for (int i = 0; i < this.arguments.expressions().size(); ++i) {
            this.arguments.expressions().get(i).toString(b, context, path, this);
            if (i < this.arguments.expressions().size() - 1) {
                b.append(",");
            }
        }
        return b.append(")");
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        if (arguments.expressions().size() == 0)
            return TensorType.empty;

        TensorType argument1Type = arguments.expressions().get(0).type(context);
        if (arguments.expressions().size() == 1)
            return argument1Type;

        TensorType argument2Type = arguments.expressions().get(1).type(context);
        return Join.outputType(argument1Type, argument2Type);
    }

    @Override
    public Value evaluate(Context context) {
        if (arguments.expressions().size() == 0)
            return DoubleValue.zero.function(function ,DoubleValue.zero);

        Value argument1 = arguments.expressions().get(0).evaluate(context);
        if (arguments.expressions().size() == 1)
            return argument1.function(function, DoubleValue.zero);

        Value argument2 = arguments.expressions().get(1).evaluate(context);
        return argument1.function(function, argument2);
    }

    /** Returns a new function node with the children replaced by the given children */
    @Override
    public FunctionNode setChildren(List<ExpressionNode> children) {
        if (arguments.expressions().size() != children.size())
            throw new IllegalArgumentException("Expected " + arguments.expressions().size() + " children but got " + children.size());
        if (children.size() == 1)
            return new FunctionNode(function, children.get(0));
        else // binary
            return new FunctionNode(function, children.get(0), children.get(1));
    }

}
